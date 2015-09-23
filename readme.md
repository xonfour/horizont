This is the **very first** release of *Horizont*, a software which is able to synchronize local user data with remote storage services like *Dropbox*.

Internally the software can dynamically adapt to competely different use cases by connecting data access and data processing modules. It follows the leading underlying principles of security and expandability. 

In the default configuration the software allows to synchronize a local folder to a remote *Dropbox* account. On the remote side files and folders are encrypted using OpenPGP. Data can be shared with other users of the system. It can be set up using a simple "Setup Wizard".

This project is the result of a master's thesis in computer science at the Technical University of Berlin. It is still in **BETA** stage so please use it with care.

# Usage

You may use JAR files (try **Horizont_0.9beta.jar** first) ready to use which include the default configuration in the *jar* sub-folder.

```
Usage: java -jar Horizont_0.9beta.jar [options]
  Options:
    -aa, --add-advanced-ui
       Add another instance of the advanced UI
       Default: false
    -as, --add-setup-wizard-ui
       Add another instance of the (simple) setup wizard UI
       Default: false
    -h, /h, --help
       Display this help/usage information
       Default: false
    -lc, --load-config
       JSON config file to load, overwriting any existing data
    -s, --storage-location
       Folder to use as storage location for internal database
       Default: /home/dust/Horizont
```

# Documentation

The *doc* sub-folder contains the master's thesis describing the background and development principles of this project (written in German language) as well as the English javadoc documentation:

**full** contains all classes (except experimental and external stuff).

**api** only contains classes relevant for using the system's API.

# Dependencies

Minimum requirement is Java Version >= 7. The project also depends on external libraries. You need them to start development:

* Bouncy Castle Crypto APIs 1.5.1 (MIT - https://www.bouncycastle.org)

* Commons Imaging 1.0-SNAPSHOT (Apache 2.0 - https://commons.apache.org/proper/commons-imaging)
	
* Commons IO 2.4 (Apache 2.0 - https://commons.apache.org/proper/commons-io)
	
* fuse-jna 2014-07-27 (BSD - http://fusejna.net)

* Gson 2.3.1 (Apache 2.0 - https://github.com/google/gson)
		
* Guava 18 (Apache 2.0 - https://github.com/google/guava)

* java7-fs-dropbox 2014-12-16 (see *src/com/github/fge* - Apache 2.0 / LGPL 3.0 - https://github.com/fge/java7-fs-dropbox)

* JGraphX 3.1.2.0 (BSD - https://github.com/jgraph/jgraphx)
	
* MigLayout 3.1.2.0 (BSD - http://www.miglayout.com)

* nio-fs-provider 1.1.0 (Apache 2.0 - https://github.com/uis-it/nio-fs-provider)
	
* OrientDB 2.1.2 (Apache 2.0 - http://orientdb.com/orientdb)

* Password Strength Meter 2010-09-23 (see *src/com/devewm/pwdstrength* - Unlicense - https://github.com/devewm/java-pwdstrength)
	
* Reflections 0.9.9-RC1 (WTFPL - https://github.com/ronmamo/reflections)

* Sardine 2015-05-12 (see *src/com/github/sardine* - Apache 2.0 - https://github.com/lookfirst/sardine)

* Sea Glass Look and Feel for Swing 0.2 (Apache 2.0 - http://seaglasslookandfeel.com)

(Maven is in preparation)

# ToDo

- Move JAR files to seperate web space

Will be added soon.
