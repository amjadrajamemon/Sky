#!/usr/bin/env python3
"""Structural checks. This is not an Android SDK build or device test."""
import pathlib, re, subprocess, sys, xml.etree.ElementTree as ET
root = pathlib.Path(__file__).resolve().parent.parent
res=root/'app/src/main/res'
java=list((root/'app/src/main/java').rglob('*.java'))
xml=list((root/'app/src/main').rglob('*.xml'))
for f in xml:
    ET.parse(f)
source='\n'.join(x.read_text() for x in java)
for typ in ['layout','id','drawable']:
    # android.R is provided by framework; require only app resources
    refs=set(re.findall(r'(?<!android\.)R\.'+typ+r'\.([\w]+)',source))
    if typ=='id':
        names=set()
        for x in xml:
            for e in ET.parse(x).iter():
                names.update(a[5:] for a in e.attrib.values() if a.startswith('@+id/'))
    else:
        names={f.stem for f in (res/typ).iterdir()}
    missing=refs-names
    if missing:raise SystemExit('Unresolved '+typ+' resources: '+str(sorted(missing)))
print(f'XML and resource references valid: {len(xml)} XML, {len(java)} Java files')
subprocess.run(['bash',str(root/'tools/run-core-tests.sh')],check=True)
print('Static checks passed. Full Android build requires Android SDK 35 + Gradle 8.9.')
