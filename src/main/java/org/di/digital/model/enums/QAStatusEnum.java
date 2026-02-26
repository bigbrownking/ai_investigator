package org.di.digital.model.enums;

public enum QAStatusEnum {
    PENDING,        // вопрос добавлен, ещё не отвечали
    TRANSCRIBING,   // аудио отправлено на транскрипцию
    TRANSCRIBED,    // текст получен, можно анализировать
    ANALYSED        // модель проанализировала, дала следующие вопросы
}
