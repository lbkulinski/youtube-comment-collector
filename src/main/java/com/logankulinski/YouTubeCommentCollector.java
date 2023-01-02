package com.logankulinski;

import java.util.Objects;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpClient;
import java.io.IOException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.util.ArrayList;
import java.util.Collections;
import java.nio.file.Path;
import java.nio.file.Files;

public final class YouTubeCommentCollector {
    private final String username;

    public YouTubeCommentCollector(String username) {
        Objects.requireNonNull(username, "the specified username is null");

        this.username = username;
    } //YouTubeCommentCollector

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record CommentData(String id, String body, String permalink) {
        public CommentData {
            if (permalink != null) {
                permalink = "https://reddit.com%s".formatted(permalink);
            } //end if
        } //CommentData
    } //CommentData

    private record Comment(String kind, CommentData data) {
    } //Comment

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record ListingData(String after, List<Comment> children) {
    } //ListingData

    private record Listing(String kind, ListingData data) {
    } //Listing

    private Listing getListing(String after) {
        String uriString;

        if (after == null) {
            uriString = "https://api.reddit.com/user/%s/comments".formatted(this.username);
        } else {
            uriString = "https://api.reddit.com/user/%s/comments?after=%s".formatted(this.username, after);
        } //end if

        URI uri = URI.create(uriString);

        HttpRequest request = HttpRequest.newBuilder(uri)
                                         .GET()
                                         .build();

        HttpResponse.BodyHandler<String> bodyHandler = HttpResponse.BodyHandlers.ofString();

        HttpClient client = HttpClient.newHttpClient();

        HttpResponse<String> response;

        try {
            response = client.send(request, bodyHandler);
        } catch (IOException | InterruptedException e) {
            throw new RuntimeException(e);
        } //end try catch

        String body = response.body();

        ObjectMapper objectMapper = new ObjectMapper();

        Listing listing;

        try {
            listing = objectMapper.readValue(body, Listing.class);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } //end try catch

        return listing;
    } //getListing

    private record YouTubeComment(String uri, String body) {
    } //YouTubeComment

    private List<YouTubeComment> extractYouTubeComments(List<Comment> comments) {
        Objects.requireNonNull(comments, "the specified Set of Comments is null");

        String regex = "(?:.|\\t|\\n|\\r)*(?:(youtu\\.be)|(youtube\\.com))(?:.|\\t|\\n|\\r)*";

        Pattern pattern = Pattern.compile(regex, Pattern.CASE_INSENSITIVE);

        return comments.stream()
                       .filter(comment -> {
                           String body = comment.data()
                                                .body();

                           Matcher matcher = pattern.matcher(body);

                           return matcher.matches();
                       })
                       .map(Comment::data)
                       .map(commentData -> {
                           String uri = commentData.permalink();

                           String body = commentData.body();

                           return new YouTubeComment(uri, body);
                       })
                       .toList();
    } //extractYouTubeComments

    private List<YouTubeComment> getYouTubeComments() {
        String after = null;

        List<YouTubeComment> youTubeComments = new ArrayList<>();

        do {
            Listing listing = this.getListing(after);

            List<Comment> comments = listing.data()
                                            .children();

            List<YouTubeComment> extractedComments = this.extractYouTubeComments(comments);

            youTubeComments.addAll(extractedComments);

            after = listing.data()
                           .after();
        } while (after != null);

        return Collections.unmodifiableList(youTubeComments);
    } //getYouTubeComments

    private void saveYouTubeComments() {
        List<YouTubeComment> youTubeComments = this.getYouTubeComments();

        ObjectMapper objectMapper = new ObjectMapper();

        String commentsJson;

        try {
            commentsJson = objectMapper.writerWithDefaultPrettyPrinter()
                                       .writeValueAsString(youTubeComments);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        } //end try catch

        String pathString = "%s-youtube-comments.json".formatted(this.username);

        Path path = Path.of(pathString);

        try {
            Files.writeString(path, commentsJson);
        } catch (IOException e) {
            throw new RuntimeException(e);
        } //end try catch
    } //saveYouTubeComments

    public static void main(String[] args) {
        if (args.length == 0) {
            throw new IllegalStateException("a username must be specified using the command-line arguments");
        } //end if

        String username = args[0];

        YouTubeCommentCollector commentCollector = new YouTubeCommentCollector(username);

        commentCollector.saveYouTubeComments();
    } //main
}