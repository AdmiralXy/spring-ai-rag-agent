package io.github.admiralxy.agent.service.provider.impl;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public class ConfluenceStorageHtmlFormatter {

    private static final Pattern EXTRA_BLANK_LINES = Pattern.compile("\\n{3,}");

    public String format(String html) {
        if (StringUtils.isBlank(html)) {
            return StringUtils.EMPTY;
        }

        Element body = Jsoup.parseBodyFragment(html).body();
        StringBuilder sb = new StringBuilder();
        renderChildren(body, sb);
        return normalize(sb.toString());
    }

    private void renderChildren(Node parent, StringBuilder sb) {
        for (Node child : parent.childNodes()) {
            renderNode(child, sb);
        }
    }

    private void renderNode(Node node, StringBuilder sb) {
        if (node instanceof TextNode textNode) {
            appendNormalizedText(sb, textNode.text());
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }

        String tag = element.tagName();
        switch (tag) {
            case "h1", "h2", "h3", "h4", "h5", "h6" -> {
                ensureBlankLine(sb);
                sb.append("## ");
                appendNormalizedText(sb, element.text());
                sb.append("\n\n");
            }
            case "p", "div", "section", "article", "blockquote" -> {
                renderChildren(element, sb);
                sb.append("\n\n");
            }
            case "br" -> sb.append("\n");
            case "ul" -> {
                for (Element li : element.children()) {
                    if (!"li".equals(li.tagName())) {
                        continue;
                    }
                    sb.append("- ");
                    renderChildren(li, sb);
                    sb.append("\n");
                }
                sb.append("\n");
            }
            case "ol" -> {
                int index = 1;
                for (Element li : element.children()) {
                    if (!"li".equals(li.tagName())) {
                        continue;
                    }
                    sb.append(index++).append(". ");
                    renderChildren(li, sb);
                    sb.append("\n");
                }
                sb.append("\n");
            }
            case "pre" -> {
                ensureBlankLine(sb);
                sb.append("```\n")
                        .append(StringUtils.defaultString(element.wholeText()).trim())
                        .append("\n```\n\n");
            }
            case "code" -> {
                sb.append('`');
                appendNormalizedText(sb, element.text());
                sb.append('`');
            }
            case "table" -> {
                renderTable(element, sb);
                sb.append("\n");
            }
            case "a" -> renderLink(element, sb);
            default -> renderChildren(element, sb);
        }
    }

    private void renderTable(Element table, StringBuilder sb) {
        List<List<String>> rows = new ArrayList<>();
        for (Element row : table.select("tr")) {
            List<String> cells = row.select("th,td").eachText();
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        if (rows.isEmpty()) {
            return;
        }

        ensureBlankLine(sb);
        for (List<String> row : rows) {
            sb.append("| ").append(String.join(" | ", row)).append(" |\n");
        }
        sb.append("\n");
    }

    private void renderLink(Element element, StringBuilder sb) {
        String text = element.text();
        String href = element.attr("href");
        if (StringUtils.isBlank(text)) {
            appendNormalizedText(sb, href);
            return;
        }

        appendNormalizedText(sb, text);
        if (StringUtils.isNotBlank(href)) {
            sb.append(" (").append(href).append(')');
        }
    }

    private void appendNormalizedText(StringBuilder sb, String text) {
        if (StringUtils.isBlank(text)) {
            return;
        }
        sb.append(text.replace('\u00A0', ' '));
    }

    private void ensureBlankLine(StringBuilder sb) {
        if (sb.isEmpty()) {
            return;
        }
        if (!sb.toString().endsWith("\n\n")) {
            if (!sb.toString().endsWith("\n")) {
                sb.append("\n");
            }
            sb.append("\n");
        }
    }

    private String normalize(String value) {
        String[] lines = value.replace("\r", StringUtils.EMPTY).split("\n", -1);
        StringBuilder normalized = new StringBuilder();
        for (String line : lines) {
            normalized.append(StringUtils.stripEnd(line, null)).append("\n");
        }
        String compacted = EXTRA_BLANK_LINES.matcher(normalized.toString()).replaceAll("\n\n");
        return compacted.trim();
    }
}
