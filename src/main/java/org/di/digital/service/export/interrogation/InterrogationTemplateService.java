package org.di.digital.service.export.interrogation;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.di.digital.dto.response.interrogation.CaseInterrogationFullResponse;
import org.di.digital.dto.response.interrogation.CaseInterrogationProtocolResponse;
import org.di.digital.dto.response.interrogation.CaseInterrogationQAResponse;
import org.di.digital.dto.response.interrogation.InterrogationTimerSessionResponse;
import org.di.digital.model.interrogation.CaseInterrogation;
import org.di.digital.model.user.User;
import org.di.digital.repository.interrogation.CaseInterrogationRepository;
import org.di.digital.util.Mapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.di.digital.util.requests.UserUtil.getCurrentUser;

@Slf4j
@Service
@RequiredArgsConstructor
public class InterrogationTemplateService {

    // ── RU форматтеры ─────────────────────────────────────────────────────────
    private static final DateTimeFormatter DATE_RU =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'года'", new Locale("ru"));
    private static final DateTimeFormatter TIME_RU =
            DateTimeFormatter.ofPattern("HH 'час.' mm 'мин.'");
    private static final DateTimeFormatter TIME_WITH_DATE_RU =
            DateTimeFormatter.ofPattern("d MMMM yyyy 'года' HH 'час.' mm 'мин.'", new Locale("ru"));

    // ── KZ форматтеры ─────────────────────────────────────────────────────────
    private static final DateTimeFormatter DATE_KZ =
            DateTimeFormatter.ofPattern("«d» MMMM yyyy 'жыл'", new Locale("kk"));

    private final Map<String, String> fileCache = new ConcurrentHashMap<>();
    private final CaseInterrogationRepository interrogationRepository;
    private final Mapper mapper;

    // ── Публичный API ─────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public String buildPreview(String role, String lang, CaseInterrogationFullResponse data) {
        boolean isDop = Boolean.TRUE.equals(data.getIsDop());
        String templateKey = isDop ? role + "_dop" : role;
        String template = loadTemplate(lang, templateKey);
        return substitute(template, lang, data);
    }

    @Transactional(readOnly = true)
    public String buildPreviewById(Long interrogationId, String lang) {
        CaseInterrogation interrogation = interrogationRepository.findById(interrogationId)
                .orElseThrow(() -> new RuntimeException("Допрос не найден: " + interrogationId));

        User user = getCurrentUser();

        CaseInterrogationFullResponse data = mapper.mapToInterrogationFullResponse(interrogation, user);
        return buildPreview(data.getRole(), lang, data);
    }

    public String getRightsText(String article, String lang) {
        return loadRights(lang, article);
    }

    // ── Подстановка плейсхолдеров ─────────────────────────────────────────────

    private String substitute(String template, String lang, CaseInterrogationFullResponse data) {
        boolean multiDay = hasMultipleDays(data.getTimerSessions(), data.getFinishedAt());
        boolean isKz = "kz".equals(lang);

        String langDefault = isKz ? "қазақ" : "русском";
        String transDefault = isKz ? "қажет етпеймін" : "не нуждаюсь";
        String involvedDef = isKz ? "басқа тұлғалар тартылмады" : "иные лица не привлекались";
        String famDefault = isKz ? "жария ету жолымен" : "путем оглашения";
        String addInfoDef = isKz ? "жоқ" : "не имею";
        String appDefault = isKz ? "қоса берілмейді" : "не прилагается";

        String result = template
                .replace("{fio}", safe(data.getFio()))
                .replace("{city}", safe(data.getCity()))
                .replace("{date}", formatDate(data.getDate(), lang))
                .replace("{caseNumber}", safe(data.getCaseNumber()))
                .replace("{room}", safe(data.getRoom()))
                .replace("{address}", safe(data.getAddrezz()))
                .replace("{language}", safeDefault(data.getLanguage(), langDefault))
                .replace("{translator}", safeDefault(data.getTranslator(), transDefault))
                .replace("{involved}", safeDefault(data.getInvolved(), involvedDef))
                .replace("{familiarization}", safeDefault(data.getFamiliarization(), famDefault))
                .replace("{additionalInfo}", safeDefault(data.getAdditionalInfo(), addInfoDef))
                .replace("{application}", safeDefault(data.getApplication(), appDefault))
                .replace("{confession}", safe(data.getConfession()))
                .replace("{lawyer}", safe(data.getLawyer()))
                .replace("{notificationNumber}", safe(data.getNotificationNumber()))
                .replace("{notificationDate}", safe(data.getNotificationDate()))
                .replace("{investigatorProfession}", safe(data.getInvestigatorProfession()))
                .replace("{investigatorFio}", formatFio(data.getInvestigator()))
                .replace("{investigatorPosition}", safe(data.getInvestigatorProfession()))
                .replace("{investigatorDepartment}", "")
                .replace("{investigatorRegion}", "")
                .replace("{timeStart}", formatTimeStart(data.getTimerSessions(), multiDay, lang))
                .replace("{timePaused}", formatTimePaused(data.getTimerSessions(), multiDay, lang))
                .replace("{timeResumed}", formatTimeResumed(data.getTimerSessions(), multiDay, lang))
                .replace("{timeEnd}", formatTimeEnd(data.getFinishedAt(), multiDay, lang));

        result = injectRights(result, lang);
        result = injectPersonTable(result, data, lang);
        result = injectQA(result, data, lang);
        return result;
    }

    // ── Маркеры прав ─────────────────────────────────────────────────────────

    private String injectRights(String text, String lang) {
        if ("kz".equals(lang)) {
            text = text.replace("[ҚҰҚЫҚТАР СТ.64]", loadRights(lang, "st64"));
            text = text.replace("[ҚҰҚЫҚТАР СТ.71]", loadRights(lang, "st71"));
            text = text.replace("[ҚҰҚЫҚТАР СТ.71 ТОЛЫҚ]", loadRights(lang, "st71_full"));
            text = text.replace("[ҚҰҚЫҚТАР СТ.78]", loadRights(lang, "st78"));
            text = text.replace("[ҚҰҚЫҚТАР СТ.65-1]", loadRights(lang, "st65_1"));
            text = text.replace("[ҚҰҚЫҚТАР СТ.80]", loadRights(lang, "st80"));
            text = text.replace("[ҚҰҚЫҚТАР СТ.82]", loadRights(lang, "st82"));
        } else {
            text = text.replace("[ПРАВА СТ.64]", loadRights(lang, "st64"));
            text = text.replace("[ПРАВА СТ.71]", loadRights(lang, "st71"));
            text = text.replace("[ПРАВА СТ.71 ПОЛНЫЕ]", loadRights(lang, "st71_full"));
            text = text.replace("[ПРАВА СТ.78]", loadRights(lang, "st78"));
            text = text.replace("[ПРАВА СТ.65-1]", loadRights(lang, "st65_1"));
            text = text.replace("[ПРАВА СТ.80]", loadRights(lang, "st80"));
            text = text.replace("[ПРАВА СТ.82]", loadRights(lang, "st82"));
        }
        return text;
    }

    // ── Таблица персональных данных ───────────────────────────────────────────

    private String injectPersonTable(String text, CaseInterrogationFullResponse data, String lang) {
        boolean isKz = "kz".equals(lang);
        String marker = isKz ? "[ЖЕКЕ ДЕРЕКТЕР КЕСТЕСІ]" : "[ТАБЛИЦА ПЕРСОНАЛЬНЫХ ДАННЫХ]";
        String markerExt = isKz ? "[ЖЕКЕ ДЕРЕКТЕР КЕСТЕСІ (КЕҢЕЙТІЛГЕН)]" : "[ТАБЛИЦА ПЕРСОНАЛЬНЫХ ДАННЫХ (РАСШИРЕННАЯ)]";

        if (text.contains(marker)) text = text.replace(marker, buildPersonTable(data, false, lang));
        if (text.contains(markerExt)) text = text.replace(markerExt, buildPersonTable(data, true, lang));
        return text;
    }

    private String buildPersonTable(CaseInterrogationFullResponse data, boolean extended, String lang) {
        boolean isKz = "kz".equals(lang);
        CaseInterrogationProtocolResponse p = data.getProtocol();
        StringBuilder sb = new StringBuilder();

        if (isKz) {
            appendRow(sb, "Тегі, аты, әкесінің аты", safe(data.getFio()));
            appendRow(sb, "Туған күні", p != null ? safe(p.getDateOfBirth()) : "—");
            appendRow(sb, "Туған жері", p != null ? safe(p.getBirthPlace()) : "—");
            appendRow(sb, "Азаматтығы", p != null ? safe(p.getCitizenship()) : "—");
            appendRow(sb, "Ұлты", p != null ? safe(p.getNationality()) : "—");
            appendRow(sb, "Білімі", formatEducations(p));
            appendRow(sb, "Отбасы жағдайы", p != null ? safe(p.getMartialStatus()) : "—");
            appendRow(sb, "Жұмыс немесе оқу орны", p != null ? safe(p.getWorkOrStudyPlace()) : "—");
            appendRow(sb, "Айналысу түрі немесе лауазымы", p != null ? safe(p.getPosition()) : "—");
            appendRow(sb, "Тұратын жері және (немесе) тіркелген жері", p != null ? safe(p.getAddress()) : "—");
            appendRow(sb, "Байланыс телефондары", p != null ? safe(p.getContactPhone()) : "—");
            appendRow(sb, "Электрондық пошта", p != null ? safe(p.getContactEmail()) : "—");
            appendRow(sb, "Күдіктіге, айыпталушыға қатынасы", formatRelations(p));
            appendRow(sb, "Әскери міндеттемеге қатынасы", formatMilitaries(p));
            appendRow(sb, "Соттылығының болуы", formatCriminals(p));
            appendRow(sb, "Жеке басын куәландыратын паспорт немесе басқа құжат",
                    p != null ? safe(p.getIinOrPassport()) : safe(data.getDocumentType()) + " " + safe(data.getNumber()));
            appendRow(sb, "Аудио/бейне тіркеу техникалық құралдарын қолдану",
                    p != null ? safe(p.getTechnical()) : "Қолданылмайды");
        } else {
            appendRow(sb, "Фамилия, имя, отчество", safe(data.getFio()));
            appendRow(sb, "Дата рождения", p != null ? safe(p.getDateOfBirth()) : "—");
            appendRow(sb, "Место рождения", p != null ? safe(p.getBirthPlace()) : "—");
            if (extended) {
                appendRow(sb, "Национальность", p != null ? safe(p.getNationality()) : "—");
                appendRow(sb, "Гражданство", p != null ? safe(p.getCitizenship()) : "—");
            } else {
                appendRow(sb, "Гражданство", p != null ? safe(p.getCitizenship()) : "—");
                appendRow(sb, "Национальность", p != null ? safe(p.getNationality()) : "—");
            }
            appendRow(sb, "Образование", formatEducations(p));
            appendRow(sb, "Семейное положение", p != null ? safe(p.getMartialStatus()) : "—");
            appendRow(sb, "Место работы или учебы", p != null ? safe(p.getWorkOrStudyPlace()) : "—");
            appendRow(sb, "Род занятий или должность", p != null ? safe(p.getPosition()) : "—");
            appendRow(sb, "Место жительства и (или) регистрации", p != null ? safe(p.getAddress()) : "—");
            appendRow(sb, "Контактные телефоны", p != null ? safe(p.getContactPhone()) : "—");
            appendRow(sb, "Электронная почта", p != null ? safe(p.getContactEmail()) : "—");
            if (!extended) appendRow(sb, "Отношение к подозреваемому, обвиняемому", formatRelations(p));
            else appendRow(sb, "Судимость", formatCriminals(p));
            appendRow(sb, "Отношение к воинской обязанности", formatMilitaries(p));
            if (!extended) appendRow(sb, "Наличие судимости", formatCriminals(p));
            appendRow(sb, "Документ, удостоверяющий личность",
                    p != null ? safe(p.getIinOrPassport()) : safe(data.getDocumentType()) + " " + safe(data.getNumber()));
            appendRow(sb, "Применение технических средств аудио/видео фиксации",
                    p != null ? safe(p.getTechnical()) : "Не применяются");
            if (extended) {
                appendRow(sb, "Иные участвующие лица", safe(data.getInvolved()));
                appendRow(sb, "Отношение потерпевшего к подозреваемому", formatRelations(p));
            }
        }
        return sb.toString();
    }

    private void appendRow(StringBuilder sb, String label, String value) {
        sb.append(String.format("%-50s | %s%n", label + ":", value));
    }

    // ── Q&A блок ─────────────────────────────────────────────────────────────

    private String injectQA(String text, CaseInterrogationFullResponse data, String lang) {
        boolean isKz = "kz".equals(lang);
        String marker = isKz ? "[АЙҒАҚТАР ЖӘНЕ СҰРАҚ-ЖАУАПТАР]" : "[ПОКАЗАНИЯ И ВОПРОСЫ-ОТВЕТЫ]";
        String questionLabel = isKz ? "Тергеушінің сұрағы" : "Вопрос следователя";
        String answerLabel = isKz ? "Жауабы" : "Ответ";
        String noAnswer = isKz ? "Жауап алынбады." : "Ответ не получен.";

        if (!text.contains(marker)) return text;

        List<CaseInterrogationQAResponse> qaList = data.getQaList();
        if (qaList == null || qaList.isEmpty()) return text.replace(marker, "");

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < qaList.size(); i++) {
            CaseInterrogationQAResponse qa = qaList.get(i);
            String answer = (qa.getAnswer() != null && !qa.getAnswer().isBlank()) ? qa.getAnswer() : noAnswer;
            if (i == 0) {
                sb.append("    ").append(answer).append("\n");
            } else {
                sb.append("\n    ").append(questionLabel).append(": ").append(safe(qa.getQuestion())).append("\n");
                sb.append("    ").append(answerLabel).append(": ").append(answer).append("\n");
            }
        }
        return text.replace(marker, sb.toString());
    }

    // ── Форматирование даты/времени ───────────────────────────────────────────

    private String formatDate(java.time.LocalDate date, String lang) {
        if (date == null) return "kz".equals(lang) ? "«___» ________ ____ жыл" : "«___» ________ ____ года";
        return "kz".equals(lang) ? date.format(DATE_KZ) : date.atStartOfDay().format(DATE_RU);
    }

    private String formatTimeValue(LocalDateTime dt, boolean showDate, String lang) {
        if (dt == null) return "kz".equals(lang) ? "«__» сағат «__» минутта" : "__ час. __ мин.";
        if ("kz".equals(lang)) {
            String timePart = "«" + String.format("%02d", dt.getHour()) + "» сағат «" + String.format("%02d", dt.getMinute()) + "» минутта";
            return showDate ? dt.toLocalDate().format(DATE_KZ) + " " + timePart : timePart;
        }
        return showDate ? dt.format(TIME_WITH_DATE_RU) : dt.format(TIME_RU);
    }

    private String formatTimeStart(List<InterrogationTimerSessionResponse> s, boolean multiDay, String lang) {
        if (s == null || s.isEmpty()) return "kz".equals(lang) ? "«__» сағат «__» минутта" : "__ час. __ мин.";
        return formatTimeValue(s.get(0).getStartedAt(), multiDay, lang);
    }

    private String formatTimePaused(List<InterrogationTimerSessionResponse> s, boolean multiDay, String lang) {
        if (s == null || s.size() < 2) return "kz".equals(lang) ? "«__» сағат «__» минутта" : "- час. - мин.";
        return formatTimeValue(s.get(0).getPausedAt(), multiDay, lang);
    }

    private String formatTimeResumed(List<InterrogationTimerSessionResponse> s, boolean multiDay, String lang) {
        if (s == null || s.size() < 2) return "kz".equals(lang) ? "«__» сағат «__» минутта" : "- час. - мин.";
        return formatTimeValue(s.get(1).getStartedAt(), multiDay, lang);
    }

    private String formatTimeEnd(LocalDateTime finishedAt, boolean multiDay, String lang) {
        return formatTimeValue(finishedAt, multiDay, lang);
    }

    private boolean hasMultipleDays(List<InterrogationTimerSessionResponse> sessions, LocalDateTime finishedAt) {
        if (sessions == null || sessions.isEmpty()) return false;
        java.time.LocalDate firstDay = sessions.get(0).getStartedAt() != null
                ? sessions.get(0).getStartedAt().toLocalDate() : null;
        if (firstDay == null) return false;
        for (InterrogationTimerSessionResponse s : sessions) {
            if (s.getStartedAt() != null && !s.getStartedAt().toLocalDate().equals(firstDay)) return true;
            if (s.getPausedAt() != null && !s.getPausedAt().toLocalDate().equals(firstDay)) return true;
        }
        return finishedAt != null && !finishedAt.toLocalDate().equals(firstDay);
    }

    // ── Загрузка файлов ───────────────────────────────────────────────────────

    private String loadTemplate(String lang, String name) {
        return loadFile("interrogations/" + lang + "/templates/" + name + ".txt");
    }

    private String loadRights(String lang, String article) {
        return loadFile("interrogations/" + lang + "/rights/" + article + ".txt");
    }

    private String loadFile(String classpath) {
        return fileCache.computeIfAbsent(classpath, key -> {
            try {
                ClassPathResource res = new ClassPathResource(key);
                try (InputStream is = res.getInputStream()) {
                    return new String(is.readAllBytes(), StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                log.error("Не удалось загрузить файл шаблона: {}", key, e);
                return "[Шаблон не найден: " + key + "]";
            }
        });
    }

    // ── Утилиты ───────────────────────────────────────────────────────────────

    private String safe(String val) {
        return (val != null && !val.isBlank()) ? val : "—";
    }

    private String safeDefault(String val, String defaultVal) {
        return (val != null && !val.isBlank() && !val.equals("—")) ? val : defaultVal;
    }

    private String formatFio(String fio) {
        if (fio == null || fio.isBlank()) return "—";
        String[] parts = fio.trim().split("\\s+");
        if (parts.length == 1) return parts[0];
        StringBuilder sb = new StringBuilder(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            if (!parts[i].isBlank()) sb.append(" ").append(Character.toUpperCase(parts[i].charAt(0))).append(".");
        }
        return sb.toString();
    }

    private String formatEducations(CaseInterrogationProtocolResponse p) {
        if (p == null || p.getEducations() == null || p.getEducations().isEmpty()) return "—";
        return p.getEducations().stream()
                .map(e -> safe(e.getType()) + (e.getAbout() != null && !e.getAbout().isBlank() ? " (" + e.getAbout() + ")" : ""))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String formatCriminals(CaseInterrogationProtocolResponse p) {
        if (p == null || p.getCriminalRecord() == null || p.getCriminalRecord().isEmpty()) return "—";
        return p.getCriminalRecord().stream()
                .map(e -> safe(e.getType()) + (e.getAbout() != null && !e.getAbout().isBlank() ? " (" + e.getAbout() + ")" : ""))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String formatMilitaries(CaseInterrogationProtocolResponse p) {
        if (p == null || p.getMilitary() == null || p.getMilitary().isEmpty()) return "—";
        return p.getMilitary().stream()
                .map(e -> safe(e.getType()) + (e.getAbout() != null && !e.getAbout().isBlank() ? " (" + e.getAbout() + ")" : ""))
                .collect(java.util.stream.Collectors.joining(", "));
    }

    private String formatRelations(CaseInterrogationProtocolResponse p) {
        if (p == null || p.getRelation() == null || p.getRelation().isEmpty()) return "—";
        return p.getRelation().stream()
                .map(e -> safe(e.getType()) + (e.getAbout() != null && !e.getAbout().isBlank() ? " (" + e.getAbout() + ")" : ""))
                .collect(java.util.stream.Collectors.joining(", "));
    }
}