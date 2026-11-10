"""Build two isolated, debug-signed test APKs. No real vault data is accessed."""
from pathlib import Path
import os,subprocess,zipfile,shutil

ROOT=Path(__file__).resolve().parents[1]
SDK=Path(os.environ.get('ANDROID_HOME',r'D:\Program\Android\Sdk'))
JDK=Path(os.environ.get('JAVA_HOME',r'D:\Program\Android\Android Studio\jbr'))
TOOLS=SDK/'build-tools/36.0.0'
ANDROID=SDK/'platforms/android-37.0/android.jar'
OUT=ROOT/'.tools/v3-vault-fixtures'
SOURCE=ROOT/'tests/android-fixtures/vault'
def run(*args):subprocess.run([str(a) for a in args],check=True,stdout=subprocess.DEVNULL)
for name,pkg,classes,components in [
    ('vault','com.kongye.docs.docktest',['FixtureVault','FixtureUnlock'],
     '<permission android:name="com.kongye.permission.VAULT" android:protectionLevel="signature" />',),
    ('login','com.kongye.floatball.fillfixture',['FixtureLogin','FixtureReport'],''),
]:
    folder=OUT/name;folder.mkdir(parents=True,exist_ok=True)
    for sub in ['classes','dex','res/values']: (folder/sub).mkdir(parents=True,exist_ok=True)
    (folder/'res/values/ids.xml').write_text('<resources><item name="url_bar" type="id" /></resources>',encoding='utf-8')
    if name=='vault':
        body='<provider android:name="com.kongye.fixture.FixtureVault" android:authorities="com.kongye.docs.docktest.vault" android:exported="true" android:permission="com.kongye.permission.VAULT" /><activity android:name="com.kongye.fixture.FixtureUnlock" android:exported="true" android:excludeFromRecents="true" android:taskAffinity="" />'
    else:
        body='<activity android:name="com.kongye.fixture.FixtureLogin" android:exported="true"><intent-filter><action android:name="android.intent.action.VIEW" /><category android:name="android.intent.category.DEFAULT" /><category android:name="android.intent.category.BROWSABLE" /><data android:scheme="https" /></intent-filter></activity><provider android:name="com.kongye.fixture.FixtureReport" android:authorities="com.kongye.floatball.fillfixture.report" android:exported="true" android:permission="com.kongye.permission.VAULT" />'
    manifest=f'<manifest xmlns:android="http://schemas.android.com/apk/res/android" package="{pkg}" android:versionCode="1" android:versionName="test-only">{components}<application android:debuggable="true" android:allowBackup="false" android:label="密码填充模拟测试" android:theme="@android:style/Theme.Material.Light.NoActionBar">{body}</application></manifest>'
    (folder/'AndroidManifest.xml').write_text(manifest,encoding='utf-8')
    run(TOOLS/'aapt2.exe','compile','--dir',folder/'res','-o',folder/'resources.zip')
    run(TOOLS/'aapt2.exe','link','-I',ANDROID,'--manifest',folder/'AndroidManifest.xml','--min-sdk-version','26','--target-sdk-version','36','-o',folder/'unsigned.apk',folder/'resources.zip')
    run(JDK/'bin/javac.exe','-encoding','UTF-8','-source','17','-target','17','-classpath',ANDROID,'-d',folder/'classes',*[SOURCE/(c+'.java') for c in classes])
    run(JDK/'bin/jar.exe','cf',folder/'classes.jar','-C',folder/'classes','.')
    env=os.environ.copy();env['JAVA_HOME']=str(JDK)
    subprocess.run([str(TOOLS/'d8.bat'),'--lib',str(ANDROID),'--min-api','26','--output',str(folder/'dex'),str(folder/'classes.jar')],env=env,check=True,stdout=subprocess.DEVNULL)
    with zipfile.ZipFile(folder/'unsigned.apk','a',zipfile.ZIP_DEFLATED) as archive:archive.write(folder/'dex/classes.dex','classes.dex')
    run(JDK/'bin/java.exe','-jar',TOOLS/'lib/apksigner.jar','sign','--ks',Path.home()/'.android/debug.keystore','--ks-key-alias','androiddebugkey','--ks-pass','pass:android','--key-pass','pass:android','--out',OUT/(name+'.apk'),folder/'unsigned.apk')
    print('Built generated-data fixture:',name)
