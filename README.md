![tagify logo](app/src/main/resources/logo.svg)

This is a CLI version of [Tagify.me](https://tagify.me) service by [Michael Dayah](https://github.com/Lucent/) rewritten in Java. With Tagify, you can use Spotify playlists as tags.

# How it works
- All your playlists prefixed with "tag:" will be treated as tags. Separate your music in any way you like.
- Every liked track that is not in tagged playlist is considered untagged. All untagged tracks will be accumulated in a special private "tag:untagged" playlist. Tagify will create one for you if you don't have it.
- The intended flow is that you simply discover and "like" new music on Spotify, run Tagify, see new tracks appear in the untagged playlist, then you can sort it manually. Next time you run Tagify, tagged tracks will be gone from the untagged playlist.

# How to use Tagify
This application requires Java 11+ already installed on your machine and your own application registered on Spotify (which is free to do). Configure your Spotify application in the following way:
- Application name: Tagify
- Redirect URI: http://localhost:42069/callback (feel free to change port number, but make sure to leave hostname as localhost)

The fat jar can be found in `build/libs` folder in a file named `tagify.jar`. First, run it with `java -jar tagify.jar`, this will create `config.cfg` in the same folder as this file. Set your Spotify application's Client ID by issuing `java -jar tagify.jar setSpotifyClientId <YOUR CLIENT ID>`. After that, you are ready to use Tagify. Run Tagify, click on authorization link then sit back and wait for Tagify to do its magic.

# Bugs
Report if you find any!
