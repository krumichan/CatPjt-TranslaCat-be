package jp.co.translacat.domain.documenttranslation;

/** 한 BE에서 허용하는 문서 전송, 처리, 보관 한도를 설정한다. */
record DocumentTranslationCapacity(int uploads, int downloads, int workers, int workspaces, long workspaceBytes) {
    static final DocumentTranslationCapacity DEFAULT = new DocumentTranslationCapacity(4, 8, 2, 4, 80L * 1024 * 1024);

    /** 기존 전송 상한을 넘지 않고 최대 크기의 문서 한 건을 예약할 수 있는지 검증한다. */
    DocumentTranslationCapacity {
        if (uploads < 1 || uploads > 4 || downloads < 1 || downloads > 8
                || workers < 1 || workers > 2 || workspaces < workers
                || workspaceBytes < 2 * DocumentTranslationService.MAX_UPLOAD_BYTES) {
            throw new IllegalArgumentException("Invalid document translation capacity");
        }
    }
}
