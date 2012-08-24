WARNING: WORK IN PROGRESS! COMMANDS AND TYPES ARE LIABLE TO CHANGE
----------------

DEPUTY
------

What is deputy?
==========
Deputy stands for dependency utility and is a command line tool that enables you to find the location of the jars you want and quickly download them. 

What does it not do?
=============
It does not build. Deputy handles dependencies. Not only does it NOT build, it does not do anything else either - it is just a utilty for dependencies. 
It is not monolitic. If you do not like that you have to perform several commands to get something interesting, you better look for some alternatives.  SBT is a great tool if you want to have a lot of control and build lots of stuff in one command. 

How does it work?
==========
Deputy is designed to work by piping the output of a command into another. 
Typically you will start with some coordinates (describing the dependencies) and end up with a list of downloaded jars.
It is up to you to define what will happen between this though or if you want to stop in between.
This makes it easy to see what goes out and in between each command and thus makes it easy to see what happens.
The problem with this is approach is that you have to know what you want.

Why was it created?
==========
When using maven and ivy I have too  often encountered issues where jars are unexpectedly put on your classpath or dependencies have failed without being able to easily see what is going on.
To make matters worse some tools fail before telling you what it was doing and why it was doing it.
And that is the simple explaination: deputy is meant to help you to be explicit about what is going on. 

What could  you use it for?
=================
You are welcome to use deputy for whatever you like, but here are some use cases it actually fits:
1) Whenever you just have some dependencies in your project: Most of the time, I just want to have the jars that I depend on, but these cannot be put under a distributed version control system. A comprimise can therefore be to have a list of urls you can download the jars you need and a tool that downloads them SUPER quick. This is something deputy will help you with.
2) Debugging ivy: for some reason your code is getting AbstractMethodErrors. You see your classpath has some jars it shouldn't have but why? With deputy you can easily figure out what is failing and why you were doing it in the first place.
3) Easily inspecting what jars and artifacts your project depends on.
4) Handle dependencies in an exteremly stable manner: base your project on links to the jars, links you know work and all surprises are gone. Adding the md5 sums for even more stability also is something that you can do.

INSTALLATION
-----------
Install conscript (TODO: link) and then do: `cs freekh/deputy` 

COMMANDS
----------
There are 4 formats that deputy operates on: 
1) coords:  These are your input and are in the format: <org>:<name>:<revision> e.g.: commons-cli:commons-cli:1.0
2) artifacts: This is the main type of deputy, containing the information deputy needs to do most of it operations and the information you need to grep for stuff
3) results: This is an aggregated list of artifacts which can be used to quickly download
4) file: the absolute file path

NOTE: notice that commands start with the name of the type it gets as an input. If the last name is of another type, it is a transformation to this type, if not the operation will output the same type.

Available commands
=============
- coords-artifacts: transform from coords to artifacts
- artifacts-check: check if artifacts can be resolved
- artifacts-transitive: transitvely find all dependencies to artifacts

COMING:
- artifacts-results: transform from artifacts to results
- results-download-file: download the list of results. outputs the location of the files which were downloaded
- results-coords: transform from results to coords
- file-dependencies-class:  in a jar or class see all the classes 
- file-declares-class: see which classes a jar declares 

PLANNED:
- sbt-artifacts: transform a sbt project into artifacts
- m2-artifacts: transform a maven2 project into artifacts
- ivy-artifacts: transform a ivy project into artifacts


Examples:
=======
Here are a couple commands to help you see how things works:
1) See all possible mutations of a dependency based on your resolver: `echo "commons-cli:commons-cli:1.0\ncommons-lang:commons-lang:2.0"  | deputy --ivy-settings=ivy-settings.xml coords-artifacts > deputy.artifacts`
2) Resolve the artifacts and to see the jars:  `cat deputy.artifacts | deputy artifacts-resolve > deputy.artifacts.checked`
3) See links to all the jars that could be resolved: `cat deputy.artifacts.checked | grep jar | deputy artifacts-results`

COOKBOOK RECIPES
----------------

Finding all the jars from a set of dependencies
=============================
`echo "commons-cli:commons-cli:1.0\ncommons-lang:commons-lang:2.0"  | deputy --ivy-settings=ivy-settings.xml coords-artifacts artifacts-resolve artifacts-transitive artifacts-results > deputy.results`


Downloading  the jars
==============
Based on the jars you found above, download them by entering:
`cat deputy.results | grep jar | deputy --ivy-settings=ivy-settings.xml  --dest=lib/[organisation]-[module]-[name].[ext] results-download 2&> deputy.jars`
Notice how quick that went compared to other depedency managment systems? The secret is that deputy starts downloading every jar immidetly and tries to find the quickest way to do so.

NOTE: If you want to build you can either just use the deputy.jars file and replace new-lines with ":" by doing: TODO COMMAND 
or simply add the directory you downloaded all the files to (in this case lib/) in the classpath: example: javac -cp lib/* ...


Downloading only what is needed
=====================
TODO:
  #iif coords has changed compared to the artifacts you got:
  ##find the new artifacts
  ##remove the jars of the artifacts you do not need
  ##download the new jars

Using deputy in your project
==================
In most situations,  it is the results you push to your version system since this is what enables you to download the jars.
The results file can be used to generate the coords. Each time you want to add or change a dependency, you generate the coords then simply change them and regenerate the results file.
Alternativly you can change the results file directly. The following script will enable you to do this:
`...`
If there are any additional changes (exclusions ...) you should be very easily script this and add it as well. 

On the build server you can check if the results file was correctly updated and check that all the jars in the project is downloadable and that your project builds with them.
For even more safty you can have a md5 list of jars you expect and have you build sever check that you have the right ones.
You can also take checksum all the urls to make sure they are correct.


Removing unused depedencies
===================
TODO: Use jad (java decompiler), grep the import statements save in a list with only distinct elements. get the list of all the jars that you depend on directly. for each jar, check if you are using an import from it.


COMING: Web containers
==========
TODO

COMING: SBS: Stupid build system
===============
Using inotify-tools,zinc (https://github.com/typesafehub/zinc), the excellent incremental compiler, and deputy you could imagine creating a "build system" which is so "stupid" and simple that it can be expressed on XXX  lines: 



COMING: using deputy with sbt
====================
SBT is a great build tool, but sometimes it can be hard to know where it's getting the jars from. This is not SBTs fault, rather a consequence of it using ivy. 
In this section you can read about using deputy with SBT.

THANKS
=====
Dean Thompson for: coming up the name deputy; the way commands look; and the way the tool works :)
Various Typesafe people for hearing me out and the encourements. 
