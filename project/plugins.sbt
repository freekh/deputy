resolvers += Resolver.url("scalasbt", new URL("http://scalasbt.artifactoryonline.com/scalasbt/sbt-plugin-releases"))(Resolver.ivyStylePatterns)

addSbtPlugin("net.databinder" % "conscript-plugin" % "0.3.4")

addSbtPlugin("com.eed3si9n" % "sbt-buildinfo" % "0.2.0")
