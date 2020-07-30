package com.jurebevc.picturehunter.utils;

public class Topic {
    private String[] topics;

    public Topic(String[] topics) {
        this.topics = topics;
    }

    public String GetTopic(int index) {
        if (index < topics.length) {
            return topics[index];
        } else {
            return "#Topic Error#";
        }
    }
}
