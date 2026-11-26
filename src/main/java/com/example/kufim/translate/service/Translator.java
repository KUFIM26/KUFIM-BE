package com.example.kufim.translate.service;

import java.util.List;

/**
 * 한국어를 영어로 옮기는 번역기.
 *
 * <p>업체를 바꿀 수 있도록 인터페이스로 둡니다. 프런트는 어떤 업체를 쓰는지 모르고
 * {@code POST /admin/translate}의 계약만 봅니다. 교체할 때는 이 구현체만 갈아 끼우면 됩니다.
 */
public interface Translator {
    /** 키가 설정되어 있는지. false면 호출하지 않고 바로 503으로 돌려보냅니다. */
    boolean configured();

    /**
     * 받은 순서 그대로, 같은 개수의 번역문을 돌려줍니다.
     *
     * @throws TranslationUnavailableException 번역 업체가 실패하거나 한도를 넘겼을 때
     */
    List<String> toEnglish(List<String> texts);
}
