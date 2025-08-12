Always use the latest (stable) version!
# SimpleWaypoints
Minecraft Fabric mod providing simple client-sided waypoints.

## Installation
1. Install the [Fabric Loader](https://fabricmc.net/use/).
2. Download the [Fabric API](https://modrinth.com/mod/fabric-api/versions/) and move it to your mods folder:
   - Linux/Windows: `.minecraft/mods`.
   - Mac: `minecraft/mods`.
3. Download SimpleWaypoints from the [releases page](https://modrinth.com/mod/SimpleWaypoints/versions/) and move it to your mods folder.

## API Usage
Replace `${simplewaypoints_version}` with the artifact version.

You may choose between my own maven repository and GitHub's package repository.
### My own
```gradle
repositories {
    maven {
        url 'https://maven.xpple.dev/maven2'
    }
}
```
### GitHub packages
```gradle
repositories {
    maven {
        url 'https://maven.pkg.github.com/xpple/SimpleWaypoints'
        credentials {
            username = System.getenv("GITHUB_USERNAME")
            password = System.getenv("GITHUB_TOKEN")
        }
    }
}
```
Import it:
```gradle
dependencies {
    include modImplementation("dev.xpple:simplewaypoints:${simplewaypoints_version}")
}
```
