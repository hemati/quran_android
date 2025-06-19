package com.appcoholic.gpt.markdown;

import android.view.View;

import com.appcoholic.gpt.data.model.Message;
import com.stfalcon.chatkit.messages.MessageHolders;

import io.noties.markwon.Markwon;

public class MarkdownIncomingTextMessageViewHolder extends MessageHolders.IncomingTextMessageViewHolder<Message> {

    private final Markwon markwon;

    public MarkdownIncomingTextMessageViewHolder(View itemView, Object payload) {
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
