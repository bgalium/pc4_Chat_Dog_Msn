package uni.cc4p1.client.connection;

import uni.cc4p1.client.model.Message;

public record QueuedMessage(Message header, byte[] payload) {}
