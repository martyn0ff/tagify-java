/*
 * This Java source file was generated by the Gradle 'init' task.
 */
package dev.martynoff.tagify;

import com.google.gson.*;
import org.apache.commons.codec.binary.Base64;
import org.apache.hc.core5.http.ParseException;
import se.michaelthelin.spotify.SpotifyApi;
import se.michaelthelin.spotify.SpotifyHttpManager;
import se.michaelthelin.spotify.exceptions.SpotifyWebApiException;
import se.michaelthelin.spotify.model_objects.credentials.AuthorizationCodeCredentials;
import se.michaelthelin.spotify.model_objects.specification.*;
import se.michaelthelin.spotify.requests.authorization.authorization_code.AuthorizationCodeUriRequest;
import se.michaelthelin.spotify.requests.authorization.authorization_code.pkce.AuthorizationCodePKCERequest;
import se.michaelthelin.spotify.requests.data.library.GetUsersSavedTracksRequest;
import se.michaelthelin.spotify.requests.data.playlists.*;
import se.michaelthelin.spotify.requests.data.users_profile.GetCurrentUsersProfileRequest;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class App {

    private final String clientId;
    private final String host;
    private final int port;
    private URI redirectUri;

    public App(String clientId, String host, int port) {
        this.clientId = clientId;
        this.port = port;
        this.host = host;
    }

    public void run() throws NoSuchAlgorithmException, InterruptedException, IOException, SpotifyWebApiException, ParseException {

        App app = new App(clientId, host, port);
        CallbackServer server = new CallbackServer(app.host, app.port);
        app.redirectUri = SpotifyHttpManager.makeUri(server.getUriString());

        MessageDigest messageDigest = MessageDigest.getInstance("SHA-256");
        String codeVerifier = app.generateCodeVerifier();
        messageDigest.update(codeVerifier.getBytes(StandardCharsets.UTF_8));
        byte[] codeChallengeRaw = messageDigest.digest();

        SpotifyApi spotifyApi = new SpotifyApi.Builder().setClientId(app.clientId)
                                                        .setRedirectUri(app.redirectUri)
                                                        .build();

        String codeChallengeBase64 = new String(Base64.encodeBase64URLSafe(codeChallengeRaw));
        String code;

        server.start();

        String authorizationCodeUri = app.authorizationCodeUri(spotifyApi, codeChallengeBase64);
        System.out.println("Use the following link to authorize Tagify: " + authorizationCodeUri);

        while ((code = server.getCode()) == null) {
            TimeUnit.MILLISECONDS.sleep(500L);
        }

        app.authorize(spotifyApi, code, codeVerifier);

        System.out.println("Looking for tags...");

        Map<String, List<String>> myTaggedTrackIdPlaylistIdsMap = app.taggedTrackIdPlaylistIdsMap(spotifyApi);

        System.out.println("Found " + myTaggedTrackIdPlaylistIdsMap.size() + " tagged tracks.");

        if (myTaggedTrackIdPlaylistIdsMap.size() > 0) {
            List<String> mySavedTracksIds = app.mySavedTracksIds(spotifyApi);
            System.out.println("Found " + mySavedTracksIds.size() + " liked tracks.");

            mySavedTracksIds.removeIf(myTaggedTrackIdPlaylistIdsMap::containsKey);

            System.out.println("Found " + mySavedTracksIds.size() + " untagged tracks.");

            System.out.println("Refreshing \"untagged\" playlist...");
            app.refreshUntaggedPlaylist(spotifyApi, mySavedTracksIds);
        }

        System.out.println("Everything is ready!");

        server.stop();

    }

    String authorizationCodeUri(SpotifyApi spotifyApi, String codeChallenge) {
        AuthorizationCodeUriRequest authorizationCodeUriRequest = spotifyApi.authorizationCodePKCEUri(codeChallenge)
                                                                            .scope("playlist-read-private," +
                                                                                   "playlist-modify-private," +
                                                                                   "playlist-modify-public," +
                                                                                   "user-library-read")
                                                                            .build();
        URI uri = authorizationCodeUriRequest.execute();
        return uri.toString();

    }

    /* Returns time left for access token to expire */
    private int authorize(SpotifyApi spotifyApi, String code, String codeVerifier) throws IOException, SpotifyWebApiException, ParseException {
        AuthorizationCodePKCERequest authorizationCodePKCERequest = spotifyApi.authorizationCodePKCE(code, codeVerifier)
                                                                              .build();
            final AuthorizationCodeCredentials authorizationCodeCredentials = authorizationCodePKCERequest.execute();

            spotifyApi.setAccessToken(authorizationCodeCredentials.getAccessToken());
            spotifyApi.setRefreshToken(authorizationCodeCredentials.getRefreshToken());

            return authorizationCodeCredentials.getExpiresIn();
    }

    private String generateCodeVerifier() {
        Random random = new Random();
        char[] allowedChars = "ABCDEFGHIJKLMNOPQRSTYVWXYZabcdefghijklmnopqrstyvwxyz0123456789_~.".toCharArray();
        int size = random.nextInt(128 - 43) + 43 + 1; // 43..128
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < size; i++) {
            int index = random.nextInt(allowedChars.length);
            sb.append(allowedChars[index]);
        }
        return sb.toString();
    }



    private String untaggedPlaylistId(SpotifyApi spotifyApi) throws IOException, SpotifyWebApiException, ParseException {


            int offset = 0;
            int limit = 50;
            Paging<PlaylistSimplified> playlistsChunk;
            GetListOfCurrentUsersPlaylistsRequest getListOfCurrentUsersPlaylistsRequest =
                    spotifyApi.getListOfCurrentUsersPlaylists()
                              .offset(offset)
                              .limit(limit)
                              .build();

            String[] untaggedPlaylistTrackId = new String[]{""};

            while ((playlistsChunk = getListOfCurrentUsersPlaylistsRequest.execute()).getItems().length != 0) {
                Arrays.stream(playlistsChunk.getItems())
                      .filter(playlist -> playlist.getName()
                                                  .equals("tag:untagged"))
                      .forEach(playlist -> untaggedPlaylistTrackId[0] = playlist.getId());
                offset += limit;
                getListOfCurrentUsersPlaylistsRequest = spotifyApi.getListOfCurrentUsersPlaylists()
                                                                  .offset(offset)
                                                                  .limit(limit)
                                                                  .build();
            }

            if (untaggedPlaylistTrackId[0].equals("")) {
                System.out.println(
                        "You don't have a playlist for untagged tracks. Creating one for you. It will be private.");
                GetCurrentUsersProfileRequest getCurrentUsersProfileRequest = spotifyApi.getCurrentUsersProfile()
                                                                                        .build();
                User me = getCurrentUsersProfileRequest.execute();
                String myUserId = me.getId();

                CreatePlaylistRequest createPlaylistRequest = spotifyApi.createPlaylist(myUserId, "tag:untagged")
                                                                        .public_(false)
                                                                        .build();
                Playlist untaggedPlaylist = createPlaylistRequest.execute();
                return untaggedPlaylist.getId();
            }

            return untaggedPlaylistTrackId[0];
    }

    private Map<String, List<String>> taggedTrackIdPlaylistIdsMap(SpotifyApi spotifyApi) throws IOException, SpotifyWebApiException, ParseException {

            int offset = 0;
            int limit = 50;
            int[] tags = new int[]{0};
            Map<String, List<String>> taggedTrackIdPlaylistIdsMap = new LinkedHashMap<>();
            Paging<PlaylistSimplified> playlistsChunk;
            GetListOfCurrentUsersPlaylistsRequest getListOfCurrentUsersPlaylistsRequest =
                    spotifyApi.getListOfCurrentUsersPlaylists()
                              .offset(offset)
                              .limit(limit)
                              .build();

            while ((playlistsChunk = getListOfCurrentUsersPlaylistsRequest.execute()).getItems().length != 0) {
                Arrays.stream(playlistsChunk.getItems())
                      .filter(playlist -> playlist.getName()
                                                  .startsWith("tag:") && !playlist.getName()
                                                                                  .endsWith("untagged"))
                      .forEach(playlist -> {
                          System.out.println(playlist.getName());
                          tags[0]++;
                          int offsetPlaylist = 0;
                          int limitPlaylist = 50;
                          GetPlaylistsItemsRequest getPlaylistsItemsRequest =
                                  spotifyApi.getPlaylistsItems(playlist.getId())
                                            .limit(limitPlaylist)
                                            .offset(offsetPlaylist)
                                            .build();
                          Paging<PlaylistTrack> tracksChunk;

                          try {
                              while ((tracksChunk = getPlaylistsItemsRequest.execute()).getItems().length != 0) {
                                  Arrays.stream(tracksChunk.getItems())
                                        .forEach(playlistTrack -> {
                                            String trackId = playlistTrack.getTrack()
                                                                          .getId();
                                            String playlistId = playlist.getId();
                                            List<String> playlistIds =
                                                    taggedTrackIdPlaylistIdsMap.get(trackId) == null ?
                                                            new ArrayList<>() :
                                                            taggedTrackIdPlaylistIdsMap.get(trackId);
                                            playlistIds.add(playlistId);
                                            taggedTrackIdPlaylistIdsMap.put(trackId, playlistIds);
                                        });

                                  offsetPlaylist += limitPlaylist;
                                  getPlaylistsItemsRequest = spotifyApi.getPlaylistsItems(playlist.getId())
                                                                       .limit(limitPlaylist)
                                                                       .offset(offsetPlaylist)
                                                                       .build();
                              }
                          } catch (IOException | SpotifyWebApiException | ParseException e) {
                              e.printStackTrace();
                          }
                      });
                offset += limit;
                getListOfCurrentUsersPlaylistsRequest = spotifyApi.getListOfCurrentUsersPlaylists()
                                                                  .offset(offset)
                                                                  .limit(limit)
                                                                  .build();
            }

            System.out.println("Found " + tags[0] + " tags.");
            if (tags[0] == 0) {
                System.out.println(
                        "That means you don't have any playlists with \"tag:\" prefix. Create some to get started.");
            }
            return taggedTrackIdPlaylistIdsMap;
    }

    private List<String> mySavedTracksIds(SpotifyApi spotifyApi) throws IOException, SpotifyWebApiException, ParseException {

            int offset = 0;
            int limit = 50;
            List<String> mySavedTracksIds = new ArrayList<>();
            Paging<SavedTrack> savedTracksChunk;
            GetUsersSavedTracksRequest getUsersSavedTracksRequest = spotifyApi.getUsersSavedTracks()
                                                                              .offset(offset)
                                                                              .limit(limit)
                                                                              .build();

            while ((savedTracksChunk = getUsersSavedTracksRequest.execute()).getItems().length != 0) {
                Arrays.stream(savedTracksChunk.getItems())
                      .forEach(savedTrack -> mySavedTracksIds.add(savedTrack.getTrack()
                                                                            .getId()));
                offset += limit;
                getUsersSavedTracksRequest = spotifyApi.getUsersSavedTracks()
                                                       .offset(offset)
                                                       .limit(limit)
                                                       .build();
            }


            return mySavedTracksIds;
    }

    private void refreshUntaggedPlaylist(SpotifyApi spotifyApi, List<String> trackIdsToAddList) throws IOException, SpotifyWebApiException, ParseException {

        int chunkSize = 100;
        String[] trackIdsToAddArray = trackIdsToAddList.toArray(new String[0]);
        List<JsonArray> trackIdsToAddJsonArrayList = new LinkedList<>();

        for (int i = 0; i < trackIdsToAddArray.length; i += chunkSize) {
            JsonArray trackIdsToAddJsonArray = new JsonArray();
            String[] chunk = Arrays.copyOfRange(trackIdsToAddArray,
                                                i,
                                                Math.min(trackIdsToAddArray.length,
                                                         i + chunkSize)); // ensures that last smaller chunk is added

            for (String trackId : chunk) {
                trackIdsToAddJsonArray.add(trackIdToTrackUriJsonElement(trackId, false));
            }

            trackIdsToAddJsonArrayList.add(trackIdsToAddJsonArray);
        }

        String untaggedPlaylistId = untaggedPlaylistId(spotifyApi);

            List<String> trackIdsToRemoveList = new ArrayList<>();
            String[] trackIdsToRemoveArray;
            List<JsonArray> trackIdsToRemoveJsonArrayList = new ArrayList<>();
            int offset = 0;
            int limit = 50;

            Paging<PlaylistTrack> tracksChunk;
            GetPlaylistsItemsRequest getPlaylistsItemsRequest = spotifyApi.getPlaylistsItems(untaggedPlaylistId)
                                                                          .limit(limit)
                                                                          .offset(offset)
                                                                          .build();

            while ((tracksChunk = getPlaylistsItemsRequest.execute()).getItems().length != 0) {

                Arrays.stream(tracksChunk.getItems())
                      .forEach(track -> trackIdsToRemoveList.add(track.getTrack()
                                                                      .getId()));
                offset += limit;
                getPlaylistsItemsRequest = spotifyApi.getPlaylistsItems(untaggedPlaylistId)
                                                     .limit(limit)
                                                     .offset(offset)
                                                     .build();
            }

            trackIdsToRemoveArray = trackIdsToRemoveList.toArray(new String[0]);

            for (int i = 0; i < trackIdsToRemoveArray.length; i += chunkSize) {
                JsonArray trackIdsToRemoveJsonArray = new JsonArray();
                String[] chunk = Arrays.copyOfRange(trackIdsToRemoveArray,
                                                    i,
                                                    Math.min(trackIdsToRemoveArray.length,
                                                             i +
                                                             chunkSize)); // ensures that last smaller chunk is added

                for (String trackId : chunk) {
                    trackIdsToRemoveJsonArray.add(trackIdToTrackUriJsonElement(trackId, true));
                }

                trackIdsToRemoveJsonArrayList.add(trackIdsToRemoveJsonArray);
            }

            for (JsonArray trackIds : trackIdsToRemoveJsonArrayList) {
                RemoveItemsFromPlaylistRequest removeItemsFromPlaylistRequest = spotifyApi.removeItemsFromPlaylist(
                                                                                                  untaggedPlaylistId,
                                                                                                  trackIds)
                                                                                          .build();
                removeItemsFromPlaylistRequest.execute(); // returns Snapshot ID, may be useful for async execution
            }

            for (JsonArray trackIds : trackIdsToAddJsonArrayList) {
                AddItemsToPlaylistRequest addItemsToPlaylistRequest = spotifyApi.addItemsToPlaylist(untaggedPlaylistId,
                                                                                                    trackIds)
                                                                                .build();
                addItemsToPlaylistRequest.execute(); // returns Snapshot ID, may be useful for async execution
            }
    }

    private JsonElement trackIdToTrackUriJsonElement(String trackId, boolean includeUriParam) {
        String trackUriJson =
                includeUriParam ? "{\"uri\":\"spotify:track:" + trackId + "\"}" : "\"spotify:track:" + trackId + "\"";
        return includeUriParam ? new Gson().fromJson(trackUriJson, JsonObject.class) : new Gson().fromJson(trackUriJson,
                                                                                                           JsonPrimitive.class);
    }
}
