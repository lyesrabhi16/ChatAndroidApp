package com.example.chatapp;

public class Configs {
    //192.168.43.147
    private static String IP = "10.2.62.78";
    private static String socketURL = "http://"+IP+":8000/";
    private static String serverURL = "http://"+IP+":8001";
    private static String signupURL = serverURL + "/signup";
    private static String signinURL = serverURL + "/signin";
    private static String userURL   = serverURL + "/user";
    private static String searchURL   = serverURL + "/search";
    private static String chatsURL = serverURL + "/chats";
    private static String messagesURL = serverURL +"/messages";

    private static String friendsURL = serverURL +"/friends";
    private static String addFriendsURL = friendsURL +"/add";


    public static String getSocketURL() {
        return socketURL;
    }

    public static void setSocketURL(String socketURL) {
        Configs.socketURL = socketURL;
    }

    public static String getServerURL() {
        return serverURL;
    }

    public static void setServerURL(String serverURL) {
        Configs.serverURL = serverURL;
    }

    public static String getSignupURL() {
        return signupURL;
    }

    public static void setSignupURL(String signupURL) {
        Configs.signupURL = signupURL;
    }

    public static String getSigninURL() {
        return signinURL;
    }

    public static void setSigninURL(String signinURL) {
        Configs.signinURL = signinURL;
    }

    public static String getUserURL() {
        return userURL;
    }

    public static void setUserURL(String userURL) {
        Configs.userURL = userURL;
    }

    public static String getSearchURL() {
        return searchURL;
    }

    public static void setSearchURL(String searchURL) {
        Configs.searchURL = searchURL;
    }

    public static String getMessagesURL() {
        return messagesURL;
    }

    public static void setMessagesURL(String messagesURL) {
        Configs.messagesURL = messagesURL;
    }

    public static String getChatsURL() {
        return chatsURL;
    }

    public static void setChatsURL(String chatsURL) {
        Configs.chatsURL = chatsURL;
    }

    public static String getFriendsURL() {
        return friendsURL;
    }

    public static void setFriendsURL(String friendsURL) {
        Configs.friendsURL = friendsURL;
    }

    public static String getAddFriendsURL() {
        return addFriendsURL;
    }
}
