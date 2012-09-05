
What is deputy?
===============
Deputy stands for dependency utility and is a command line tool that
helps you inspect ivy and maven dependencies. Continue reading to learn more!


Installation
==============
RIGHT NOW: compile using sbt then add this folder on the PATH

COMING: Install `conscript`_ and then do:

.. code-block:: bash

   #WILL NOT WORK: cs freekh/deputy

Getting started
================
To learn more about available commands and options, run:

.. code-block:: bash

  deputy --help


How it works
================
Deputy works by piping in a format then emitting a new one. You can
also do different operations on input in different formats. 

There are 4 formats in deputy on: 

1. deps:  Dependencies are your input and are in the format: <org>:<name>:<revision> e.g.: commons-cli:commons-cli:1.0

2. resolved: This is a resolved dependency with more information

3. results: This is an aggregated list of resolved deps which can be used to quickly download

4. file: the absolute file path

**NOTICE**: All commands start with the name of the type it gets as an
input. 
If the last name is of another type, it is a transformation to this type, if not the operation will output the same type.




Cookbook
====================

1. Download the dependencies you need to compile from io.netty:netty:3.5.0.Final

.. code-block:: bash

  echo io.netty:netty:3.5.0.Final | deputy deps-resolved | deputy --quick --grep="\|.*compile.*\|" resolved-transitive | deputy resolved-highest-versions  | deputy resolved-results | grep -v "#pom" | grep -v "#ivy" | deputy results-download-file

2. Inspect all dependencies io.netty:netty:3.5.0.Final by printing them out in a tree

.. code-block:: bash

  echo io.netty:netty:3.5.0.Final | ./deputy deps-resolved | ./deputy resolved-transitive | ./deputy resolved-treeprint

3. Print out  dependencies that has pruned because there exists a higher version

.. code-block:: bash

  echo io.netty:netty:3.5.0.Final | ./deputy deps-resolved | ./deputy --quick resolved-transitive | ./deputy resolved-highest-versions | sort > highest_only #put only the highest versions found in a file

.. code-block:: bash

  echo io.netty:netty:3.5.0.Final | ./deputy deps-resolved | ./deputy resolved-transitive | sort > all  #put all versions in a file

.. code-block:: bash

  diff all highest_only |  grep "< " | sed 's/< //' | cut -d '|' -f 1 | uniq

4. Print all dependencies needed to compile with play:play_2.9.1:2.0.3 using a resolver from the ivy-settings.xml file below:

.. code-block:: xml 
  
  <ivysettings>
    <settings defaultResolver="typesafe"/>
    <resolvers>
      <ibiblio name="typesafe" m2compatible="true" root="http://repo.typesafe.com/typesafe/releases/"/>
    </resolvers>
  </ivysettings>


.. code-block:: bash

  echo play:play_2.9.1:2.0.3 | ./deputy --ivy-settings=ivy-settings.xml --resolver=typesafe deps-resolved | ./deputy --ivy-settings=ivy-settings.xml --resolver=typesafe resolved-transitive |  ./deputy resolved-treeprint

  
Design and scope
==========================

What does it not do?
--------------------------------
It does not build. Deputy handles dependencies. Not only does it NOT build, it does not do anything else either - it is just a utility for dependencies. 

It is not monolitic. If you do not like that you have to perform several commands to get something interesting, you better look for some alternatives.  SBT is a great tool if you want to have a lot of control and build lots of stuff in one command. 


How does it work?
-------------------------------
Deputy is designed to work by piping the output of a command into another. 

Typically you will start with some coordinates (describing the dependencies) and end up with a list of downloaded jars.

It is up to you to define what will happen between this though or if you want to stop in between.

This makes it easy to see what goes out and in between each command and thus makes it easy to see what happens.

The problem with this is approach is that you have to know what you want.


Why was it created?
-------------------------------
When using maven and ivy I have too  often encountered issues where jars are unexpectedly put on your classpath or dependencies have failed without being able to easily see what is going on.

To make matters worse some tools fail before telling you what it was doing and why it was doing it.

And that is the simple explanation: deputy is meant to help you to be explicit about what is going on. 


What could  you use it for?
-------------------------------
You are welcome to use deputy for whatever you like, but here are some use cases it actually fits:

1. Whenever you just have some dependencies in your project: Most of the time, I just want to have the jars that I depend on, but these cannot be put under a distributed version control system. A compromise   can therefore be to have a list of urls you can download the jars you need and a tool that downloads them SUPER quick. This is something deputy will help you with.

2. Debugging ivy: for some reason your code is getting AbstractMethodErrors. You see your classpath has some jars it shouldn't have but why? With deputy you can easily figure out what is failing and why you were doing it in the first place.

3. Easily inspecting what jars and artifacts your project depends on.

4. Handle dependencies in an extremely  stable manner: base your project on links to the jars, links you know work and all surprises are gone. Adding the md5 sums for even more stability also is something that you can do.


LICENSE
=======

This code is open source software licensed under the `Apache 2.0 License`_. Feel free to use it accordingly.


THANKS
==========
Dean Thompson for: coming up the name deputy; the way commands look; and the way the tool works :)

Various Typesafe people for hearing me out and the encouragements. 

.. _`conscript`: https://github.com/n8han/conscript/
.. _`zinc`: https://github.com/typesafehub/zinc/
.. _`Apache 2.0 License`: http://www.apache.org/licenses/LICENSE-2.0.html
