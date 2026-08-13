# Cobblemon OneBlock — Developer API

Everything a third-party mod needs to extend Cobblemon OneBlock without touching the core.

- **Module:** `cobblemon-oneblock-api` (Java 21, Fabric 1.21.1)
- **Depends on:** Fabric API only — **not** on `cobblemon-oneblock-core`, **not** on Cobblemon
- **Working reference:** [`example-addon/`](example-addon) — builds in this repository, so a
  breaking API change fails the build instead of silently rotting in the docs

---

## 1. Adding the dependency

```kotlin
repositories {
    maven("https://maven.pkg.github.com/SK4NDULF/Cobblemon-OneBlock")
    // or JitPack:
    // maven("https://jitpack.io")
}

dependencies {
    modImplementation("io.github.sk4ndulf.cobblemon.oneblock:cobblemon-oneblock-api:0.1.0")
}
```

In `fabric.mod.json`:

```json
"depends": {
  "cobblemon_oneblock_api": "*"
}
```

Depend on `cobblemon_oneblock_api`, not on `cobblemon_oneblock` — your addon then also loads on a server that
only ships the API, and you never pull Cobblemon into your build.

## 2. Getting the API

The core creates the API during its own initialization. The earliest always-safe moment for
an addon is Fabric's `SERVER_STARTING`:

```java
ServerLifecycleEvents.SERVER_STARTING.register(server -> {
    if (!OneBlockAPI.isAvailable()) return;   // core not installed — stay idle
    OneBlockAPI api = OneBlockAPI.get();
});
```

`OneBlockAPI.get()` throws `IllegalStateException` when the core has not initialized yet, so
never call it from a static initializer. `isAvailable()` makes your addon a soft dependency.

Unless stated otherwise, **every API method must be called on the server thread.**

---

## 3. Managers

`OneBlockAPI` exposes one accessor per subsystem. All of them are read-only views — state is
changed through player commands, and you observe those changes through events.

| Accessor | Interface | What it answers |
|---|---|---|
| `islandManager()` | `IslandManager` | island of a player, island in a grid slot, all active islands |
| `permissionManager()` | `PermissionManager` | a player's role on an island, may they modify a position |
| `partyManager()` | `PartyManager` | pending invites, configured max party size |
| `progressionManager()` | `ProgressionManager` | current points, points needed per level, border size per level |
| `eventManager()` | `EventManager` | register trigger event types, query the running event |
| `lootRegistry()` | `LootRegistry` | register custom OneBlock loot providers |
| `eventBus()` | `OneBlockEventBus` | subscribe to everything below |

`Island` is a **live view**: values change as the island progresses, so read fields when you
need them instead of caching the object's state. Don't hold `members()` past the current tick.

---

## 4. Event bus

```java
api.eventBus().subscribe(BorderLevelUpEvent.class, event -> {
    LOGGER.info("Island {} reached level {}", event.island().id(), event.newLevel());
});
```

Subscribing to a superclass also receives its subclasses — subscribe to `OneBlockEvent` to see
everything. Priorities run `HIGH` → `NORMAL` → `LOW`, later listeners see earlier decisions.
`subscribe(...)` returns an `EventSubscription` you can `unsubscribe()` later.

**A listener that throws is caught and logged.** A broken addon never takes down the core or
other listeners.

### Event catalogue

| Event | Fired when | Notable accessors |
|---|---|---|
| `IslandCreatedEvent` | island created, OneBlock placed, before the owner is teleported | `island()`, `owner()` |
| `OneBlockBreakEvent` | after a break and its regeneration | `island()`, `player()`, `brokenState()`, `nextState()` |
| `BorderLevelUpEvent` | island reached a new border level (border already grown) | `oldLevel()`, `newLevel()`, `newBorderSize()` |
| `PartyJoinEvent` | player accepted an invite | `island()`, `player()` |
| `PartyLeaveEvent` | membership ended | `reason()`: `LEFT`, `KICKED`, `ISLAND_ARCHIVED` |
| `PermissionChangeEvent` | a player's role on an island changed | `oldRole()`, `newRole()` |
| `TriggerEventStartEvent` | a trigger event started | `eventTypeId()`, `difficulty()` |
| `TriggerEventEndEvent` | trigger event succeeded | `eventTypeId()` |
| `TriggerEventFailEvent` | trigger event failed, timed out, or was abandoned | `eventTypeId()` |
| `AuditEvent` | any moderation action (create, reset, ban, admin override, ...) | `action()`, `actor()`, `target()`, `details()` |

`AuditEvent` is the hook for external moderation tooling — a Discord bot, a dashboard, an
external database. The core already writes these to `config/cobblemon_oneblock/audit.log`.

Events implementing `Cancellable` can be prevented; cancelled events are still delivered to
the remaining listeners so they can observe the cancellation.

---

## 5. Extension point: custom trigger events

The core handles queueing, cooldown, timeout, announcements and cleanup. Your type only
describes what happens.

```java
public class MeteorShowerEvent implements TriggerEventType {

    @Override public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath("yourmod", "meteor_shower");
    }

    @Override public String displayName() { return "Meteor Shower"; }

    @Override public ActiveTriggerEvent start(TriggerEventContext context) {
        return new Instance();
    }

    private static final class Instance implements ActiveTriggerEvent {
        private int ticks = 0;

        @Override public TriggerEventStatus tick(TriggerEventContext context) {
            ticks++;
            // context.difficulty() is the island's border level (1-8) captured at start
            return ticks >= 200 ? TriggerEventStatus.SUCCESS : TriggerEventStatus.RUNNING;
        }

        @Override public void cleanup(TriggerEventContext context) {
            // despawn anything you created — called on success, failure, timeout,
            // island archive and server stop
        }
    }
}

api.eventManager().registerEventType(new MeteorShowerEvent());
```

`TriggerEventContext` gives you `island()`, `level()`, `anchor()`, `difficulty()` and
`participants()` (recomputed on every call — online island players in the OneBlock world).

**Project rule — please keep it:** difficulty scales with the border level, **rewards do not**.
A boss on level 8 is harder than on level 2 and drops exactly the same. Granting rewards is
your event's own job, which is also why there is no separate "reward provider" interface.

Registering an already-taken id replaces the previous type (logged), so you can deliberately
override a built-in event. The core ships `cobblemon_oneblock:mob_wave`, `cobblemon_oneblock:boss_fight`,
`cobblemon_oneblock:resource_burst` and `cobblemon_oneblock:legendary_encounter`.

**Failure handling:** an exception in `start` drops that trigger; an exception in `tick`
fails the event and runs `cleanup`. Your bugs cannot wedge the core.

---

## 6. Extension point: custom loot providers

Decide what the OneBlock turns into.

```java
public class NetherPhaseProvider implements OneBlockLootProvider {

    @Override public ResourceLocation id() {
        return ResourceLocation.fromNamespaceAndPath("yourmod", "nether_phase");
    }

    @Override public Optional<BlockState> nextBlock(Island island, ServerLevel level) {
        if (island.borderLevel() >= 5) {
            return Optional.of(Blocks.NETHERRACK.defaultBlockState());
        }
        return Optional.empty();   // defer to the next provider / the configured pool
    }
}

api.lootRegistry().register(new NetherPhaseProvider());
```

Providers are asked in registration order; the first non-empty answer wins. Returning
`Optional.empty()` is the important half of the contract: it lets you handle only the cases
you care about while the server's own loot table keeps working for everything else.
Called once per break on the server thread — keep it fast and never block.

Server owners configure the base pool in `config/cobblemon_oneblock/loottable.json5` (default: every
breakable, fluid-free block from Minecraft, Cobblemon and every other installed mod).

**Precedence.** Providers → treasure chest roll → configured block pool. A provider that
answers therefore also suppresses the chest for that break: it asked for a specific block,
it gets that block. If you want your phase to keep handing out chests, return
`Optional.empty()` for the breaks you do not care about rather than pinning every one.

---

## 7. Versioning

`cobblemon-oneblock-api` follows semantic versioning:

- **Patch** (`0.1.0` → `0.1.1`): implementation fixes, JavaDoc, no signature changes.
- **Minor** (`0.1.0` → `0.2.0`): new interfaces, new methods with `default` implementations,
  new events. Existing addons keep compiling and running.
- **Major** (`0.x` → `1.0`): removals or signature changes. Announced in the changelog with a
  migration note.

While the API is at `0.x` it is not frozen yet — breaking changes may still land in minor
versions, but each one is listed in the changelog. `OneBlockAPI.apiVersion()` returns the
implementation's version at runtime if you need to branch on it.

Adding an event type or a loot provider is never a breaking change for other addons.
