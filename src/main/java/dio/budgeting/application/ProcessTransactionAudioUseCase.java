package dio.budgeting.application;

import org.springframework.ai.audio.transcription.TranscriptionModel;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

@Service
public class ProcessTransactionAudioUseCase {

    private final TranscriptionModel transcriptionModel;
    private final ChatClient chatClient;

    public ProcessTransactionAudioUseCase(
            TranscriptionModel transcriptionModel,
            ChatClient chatClient
    ) {
        this.transcriptionModel = transcriptionModel;
        this.chatClient = chatClient;
    }

    public String execute(Resource audio) {
        var transcription = transcriptionModel.transcribe(audio);
        return chatClient
                .prompt()
                .user(transcription)
                .call()
                .content();
    }

}