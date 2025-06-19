package com.appcoholic.gpt.markdown;

import android.view.View;

import com.appcoholic.gpt.data.model.Message;
import com.stfalcon.chatkit.messages.MessageHolders;

import io.noties.markwon.Markwon;

public class MarkdownOutcomingTextMessageViewHolder extends MessageHolders.OutcomingTextMessageViewHolder<Message> {

    private final Markwon markwon;

    public MarkdownOutcomingTextMessageViewHolder(View itemView, Object payload) {
        super(itemView, payload);
        markwon = Markwon.create(itemView.getContext());
    }

    @Override
    public void onBind(Message message) {
        super.onBind(message);
        if (text != null) {
            markwon.setMarkdown(text, message.getText());
        }
    }
}
