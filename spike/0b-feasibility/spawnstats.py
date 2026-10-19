import sys,re,collections
isl={0:'hub',3072:'plains/grass',6144:'desert/sand',9216:'ocean/water',12288:'plains/stone',15360:'plains/planks'}
c=collections.defaultdict(collections.Counter); b=collections.defaultdict(set); t=collections.defaultdict(collections.Counter)
for l in sys.stdin:
  m=re.search(r'pos=(-?\d+), (-?\d+), (-?\d+) biome=(\S+) species=(\S+).*type=(\S+)',l)
  if not m: continue
  x=int(m.group(1)); k=min(isl,key=lambda a:abs(a-x)); c[isl[k]][m.group(5)]+=1; b[isl[k]].add(m.group(4)); t[isl[k]][m.group(6).replace('SpawnablePosition','')]+=1
for k in isl.values(): print(f"{k:15s} {sum(c[k].values()):4d} {sorted(b[k])} {dict(t[k])} {dict(c[k].most_common(10))}")
