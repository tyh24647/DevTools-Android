"""Device smoke test: local API authorization, optimistic writes and profile isolation.
Requires a debuggable DevTools build running on one adb device. Never prints its token.
"""
import json, subprocess, urllib.request, urllib.error, xml.etree.ElementTree as ET
ADB='/Users/tylerhostager/Library/Android/sdk/platform-tools/adb'
PACKAGE='com.tyh24647.devtools.debug'
def adb(*args): return subprocess.check_output([ADB,*args],text=True)
xml=adb('shell','run-as',PACKAGE,'cat','shared_prefs/firefox-pairing.xml')
token=next(row.text for row in ET.fromstring(xml) if row.attrib.get('name')=='token')
def settings(): return json.loads(adb('shell','run-as',PACKAGE,'cat','files/configuration.json'))
original_app=settings()
adb('forward','tcp:18747','tcp:18746')
def request(patch=None,auth=True):
    headers={'Authorization':f'Bearer {token}'} if auth else {}
    if patch: headers['Content-Type']='application/json'
    req=urllib.request.Request('http://127.0.0.1:18747/firefox',data=json.dumps(patch).encode() if patch else None,headers=headers)
    try:
        with urllib.request.urlopen(req,timeout=6) as response: return response.status,json.load(response)
    except urllib.error.HTTPError as error: return error.code,json.load(error)
try:
    assert request(auth=False)[0]==401
    code,original=request();assert code==200
    assert original['enabled']==original_app['browserProfiles']['firefox']['enabled']
    assert 'token' not in original and 'browserProfiles' not in original
    code,changed=request({'revision':original['revision'],'enabled':not original['enabled']});assert code==200
    assert changed['enabled']!=original['enabled']
    assert settings()['enabled']==original_app['enabled'], 'Firefox toggle changed embedded browser'
    assert request({'revision':original['revision'],'enabled':original['enabled']})[0]==409
    assert request({'revision':changed['revision'],'enabled':'bad'})[0]==400
    code,restored=request({'revision':changed['revision'],'enabled':original['enabled']});assert code==200
    assert restored['enabled']==original['enabled']
    print('PASS: unauthenticated access rejected; Firefox profile reads/writes, conflict rejection and embedded-browser isolation verified; original toggle restored.')
finally: adb('forward','--remove','tcp:18747')
