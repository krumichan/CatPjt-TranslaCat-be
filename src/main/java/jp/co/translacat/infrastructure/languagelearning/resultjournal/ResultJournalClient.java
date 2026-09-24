package jp.co.translacat.infrastructure.languagelearning.resultjournal;

public interface ResultJournalClient {
    ResultAcknowledgement deliver(ResultEnvelope envelope);
}
