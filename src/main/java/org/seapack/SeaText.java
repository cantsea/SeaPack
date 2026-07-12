package org.seapack;

import java.util.List;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;

public final class SeaText {
    private static final MiniMessage MINI_MESSAGE = MiniMessage.miniMessage();
    private static final LegacyComponentSerializer LEGACY_AMPERSAND = LegacyComponentSerializer.legacyAmpersand();
    private static final LegacyComponentSerializer LEGACY_SECTION = LegacyComponentSerializer.legacySection();
    private static final PlainTextComponentSerializer PLAIN_TEXT = PlainTextComponentSerializer.plainText();
    private static final Pattern LEGACY_COLOR_PATTERN = Pattern.compile("(?i).*[&§][0-9A-FK-OR].*");
    private static final Pattern MINI_MESSAGE_TAG_PATTERN = Pattern.compile(".*</?[#a-zA-Z][^>]*>.*");

    private SeaText() {
    }

    public static Component component(String text) {
        Component component;
        if (text == null || text.isBlank()) {
            component = Component.empty();
        } else if (usesLegacyColors(text) && !usesMiniMessage(text)) {
            component = text.indexOf('§') >= 0
                    ? LEGACY_SECTION.deserialize(text)
                    : LEGACY_AMPERSAND.deserialize(text);
        } else {
            component = parseMiniMessage(text);
        }
        return withoutDefaultItalic(component);
    }

    public static List<Component> components(List<String> lines) {
        return lines.stream()
                .map(SeaText::component)
                .toList();
    }

    public static String plain(String text) {
        return PLAIN_TEXT.serialize(component(text));
    }

    public static Component plainComponent(String text) {
        return withoutDefaultItalic(Component.text(text == null ? "" : text));
    }

    private static boolean usesLegacyColors(String text) {
        return LEGACY_COLOR_PATTERN.matcher(text).matches();
    }

    private static boolean usesMiniMessage(String text) {
        return MINI_MESSAGE_TAG_PATTERN.matcher(text).matches();
    }

    private static Component parseMiniMessage(String text) {
        try {
            return MINI_MESSAGE.deserialize(text);
        } catch (RuntimeException ignored) {
            return Component.text(text);
        }
    }

    private static Component withoutDefaultItalic(Component component) {
        if (component instanceof TextComponent textComponent) {
            return textComponent.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
        }
        return component.decorationIfAbsent(TextDecoration.ITALIC, TextDecoration.State.FALSE);
    }
}
