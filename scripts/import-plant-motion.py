"""Compile user-provided SVG scenes to offline Android vectors and motion data."""
import re,json,sys,xml.etree.ElementTree as E
from pathlib import Path
source=Path(sys.argv[1]).read_text(encoding='utf-8')
root=Path(__file__).resolve().parents[1]/'app/src/main'; assets=root/'assets/branding/plant-motion';assets.mkdir(parents=True,exist_ok=True)
smil=re.search(r'const smilClickJump\s*=\s*`([\s\S]*?)`;',source).group(1)
svgs=re.findall(r'svg:\s*`([\s\S]*?)`',source)
assert len(svgs)==6
count=0;res=[];scenes=[]
def nums(text):return [float(x) for x in re.findall(r'-?\d*\.?\d+',text)]
def frame(text):
 v=[0,0,1,1,0,1]
 for name,args in re.findall(r'(translateY|translateX|translate|scaleY|scaleX|scale|rotate)\(([^)]+)\)',text):
  a=nums(args)
  if name=='translate':v[0]=a[0];v[1]=a[1] if len(a)>1 else 0
  if name=='translateX':v[0]=a[0]
  if name=='translateY':v[1]=a[0]
  if name=='scale':v[2]=a[0];v[3]=a[1] if len(a)>1 else a[0]
  if name=='scaleX':v[2]=a[0]
  if name=='scaleY':v[3]=a[0]
  if name=='rotate':v[4]=a[0]
 op=re.search(r'opacity:\s*([\d.]+)',text)
 if op:v[5]=float(op.group(1))
 return v
for i,raw in enumerate(svgs):
 raw=raw.replace('${smilClickJump}',smil);(assets/f'plant-{i+1}.svg').write_text(raw,encoding='utf-8')
 svg=E.fromstring(raw);style=svg.find('{http://www.w3.org/2000/svg}style').text
 animations={}
 for match in re.finditer(r'@keyframes\s+(\S+)\s*\{',style):
  start=match.end();depth=1;end=start
  while depth:
   if style[end]=='{':depth+=1
   if style[end]=='}':depth-=1
   end+=1
  frames={}
  for keys,values in re.findall(r'([\d\s%,.]+)\{([^}]+)\}',style[start:end-1]):
   for key in nums(keys):frames[key/100]=frame(values)
  animations[match.group(1)]=[[k,*frames[k]] for k in sorted(frames)]
 motions={}
 for name,body in re.findall(r'\.([\w-]+)\s*\{([^}]+)\}',style):
  am=re.search(r'animation:\s*([\w-]+)\s+([\d.]+)s\s+([^;]+)',body)
  if not am:continue
  ease=am.group(3);curve=re.search(r'cubic-bezier\(([^)]+)\)',ease)
  points=nums(curve.group(1)) if curve else [0,0,.58,1] if 'ease-out' in ease else [.42,0,1,1] if 'ease-in ' in ease else [.42,0,.58,1]
  origin=re.search(r'transform-origin:\s*([^;]+)',body);delay=re.search(r'(-[\d.]+)s',ease)
  motions[name]={'origin':nums(origin.group(1)) if origin else [0,0],'duration':float(am.group(2))*1000,'delay':float(delay.group(1))*1000 if delay else 0,'ease':points,'frames':animations[am.group(1)]}
 def walk(el,inherit):
  global count
  tag=el.tag.split('}')[-1]
  if tag not in ['svg','g','path','circle']:return None
  attrs=dict(inherit);attrs.update(el.attrib);node={}
  if 'class' in el.attrib:node['motion']=el.attrib['class']
  if el.get('opacity'):node['opacity']=float(el.get('opacity'))
  if tag in ['path','circle']:
   if tag=='path':data=el.get('d')
   else:
    x=float(el.get('cx',0));y=float(el.get('cy',0));r=float(el.get('r'));data=f'M{x-r},{y}a{r},{r} 0 1,0 {2*r},0a{r},{r} 0 1,0 {-2*r},0'
   at={'pathData':data,'fillColor':attrs.get('fill','#00000000')}
   if at['fillColor']=='none':at['fillColor']='#00000000'
   if attrs.get('stroke'):at.update(strokeColor=attrs['stroke'],strokeWidth=attrs.get('stroke-width','1'),strokeLineCap=attrs.get('stroke-linecap','butt'),strokeLineJoin=attrs.get('stroke-linejoin','miter'))
   count+=1
   shape={'fill':at['fillColor'],'stroke':attrs.get('stroke'),'width':float(attrs.get('stroke-width',1)),'cap':attrs.get('stroke-linecap','butt'),'join':attrs.get('stroke-linejoin','miter')}
   if tag=='circle':shape['circle']=[x,y,r]
   else:
    tokens=re.findall(r'[A-Za-z]|[-+]?(?:\d*\.\d+|\d+\.?\d*)(?:[eE][-+]?\d+)?',data);ops=[];pos=0;command=None;arity={'M':2,'L':2,'H':1,'V':1,'C':6,'Z':0}
    while pos<len(tokens):
     if tokens[pos].isalpha():command=tokens[pos];pos+=1
     assert command in arity,command
     n=arity[command];args=[float(x) for x in tokens[pos:pos+n]];pos+=n;ops.append([command,*args])
     if command=='M':command='L'
     if command=='Z':command=None
    shape['commands']=ops
   node['shape']=shape
  children=[n for child in el if (n:=walk(child,attrs)) is not None]
  if children:node['children']=children
  return node
 scenes.append({'motions':motions,'tree':walk(svg,{})})
(assets/'scenes.json').write_text(json.dumps(scenes,separators=(',',':')),encoding='utf-8')
print(f'Compiled {len(scenes)} scenes, {count} native path shapes and {sum(len(x["motions"]) for x in scenes)} animated groups.')
