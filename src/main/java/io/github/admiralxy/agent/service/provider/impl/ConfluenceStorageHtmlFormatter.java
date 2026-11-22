package io.github.admiralxy.agent.service.provider.impl;

import org.apache.commons.lang3.StringUtils;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.nodes.Node;
import org.jsoup.nodes.TextNode;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

public class ConfluenceStorageHtmlFormatter {

    private static final Pattern EXTRA_BLANK_LINES = Pattern.compile("\\n{3,}");
    private static final Set<String> HEADINGS = Set.of("h1", "h2", "h3", "h4", "h5", "h6");
    private static final Set<String> BLOCKS = Set.of("p", "div", "section", "article", "blockquote");
    private static final String BR = "br";
    private static final String UL = "ul";
    private static final String OL = "ol";
    private static final String LI = "li";
    private static final String PRE = "pre";
    private static final String CODE = "code";
    private static final String TABLE = "table";
    private static final String LINK = "a";
    private static final String ROW = "tr";
    private static final String CELLS = "th,td";
    private static final String HREF = "href";
    private static final String LINE = "\n";
    private static final String PARAGRAPH = "\n\n";
    private static final String CARRIAGE_RETURN = "\r";
    private static final String HEADING_PREFIX = "## ";
    private static final String BULLET = "- ";
    private static final String NUMBERED = "%d. ";
    private static final String FENCE_OPEN = "```\n";
    private static final String FENCE_CLOSE = "\n```\n\n";
    private static final String CELL_OPEN = "| ";
    private static final String CELL_SEPARATOR = " | ";
    private static final String CELL_CLOSE = " |\n";
    private static final String LINK_OPEN = " (";
    private static final char LINK_CLOSE = ')';
    private static final char BACKTICK = '`';
    private static final char NBSP = ' ';
    private static final char SPACE = ' ';

    public String format(String html) {
        if (StringUtils.isBlank(html)) {
            return StringUtils.EMPTY;
        }
        StringBuilder sb = new StringBuilder();
        renderChildren(Jsoup.parseBodyFragment(html).body(), sb);
        return normalize(sb.toString());
    }

    private void renderChildren(Node parent, StringBuilder sb) {
        for (Node child : parent.childNodes()) {
            renderNode(child, sb);
        }
    }

    private void renderNode(Node node, StringBuilder sb) {
        if (node instanceof TextNode textNode) {
            appendText(sb, textNode.text());
            return;
        }
        if (!(node instanceof Element element)) {
            return;
        }
        String tag = element.tagName();
        if (HEADINGS.contains(tag)) {
            ensureBlankLine(sb);
            sb.append(HEADING_PREFIX);
            appendText(sb, element.text());
            sb.append(PARAGRAPH);
        } else if (BLOCKS.contains(tag)) {
            renderChildren(element, sb);
            sb.append(PARAGRAPH);
        } else {
            renderInline(element, tag, sb);
        }
    }

    private void renderInline(Element element, String tag, StringBuilder sb) {
        switch (tag) {
            case BR -> sb.append(LINE);
            case UL -> renderList(element, sb, false);
            case OL -> renderList(element, sb, true);
            case PRE -> {
                ensureBlankLine(sb);
                sb.append(FENCE_OPEN).append(StringUtils.defaultString(element.wholeText()).trim()).append(FENCE_CLOSE);
            }
            case CODE -> {
                sb.append(BACKTICK);
                appendText(sb, element.text());
                sb.append(BACKTICK);
            }
            case TABLE -> {
                renderTable(element, sb);
                sb.append(LINE);
            }
            case LINK -> renderLink(element, sb);
            default -> renderChildren(element, sb);
        }
    }

    private void renderList(Element list, StringBuilder sb, boolean numbered) {
        int index = 1;
        for (Element item : list.children()) {
            if (!LI.equals(item.tagName())) {
                continue;
            }
            sb.append(numbered ? NUMBERED.formatted(index++) : BULLET);
            renderChildren(item, sb);
            sb.append(LINE);
        }
        sb.append(LINE);
    }

    private void renderTable(Element table, StringBuilder sb) {
        List<List<String>> rows = new ArrayList<>();
        for (Element row : table.select(ROW)) {
            List<String> cells = row.select(CELLS).eachText();
            if (!cells.isEmpty()) {
                rows.add(cells);
            }
        }
        if (rows.isEmpty()) {
            return;
        }
        ensureBlankLine(sb);
        for (List<String> row : rows) {
            sb.append(CELL_OPEN).append(String.join(CELL_SEPARATOR, row)).append(CELL_CLOSE);
        }
        sb.append(LINE);
    }

    private void renderLink(Element element, StringBuilder sb) {
        String text = element.text();
        String href = element.attr(HREF);
        if (StringUtils.isBlank(text)) {
            appendText(sb, href);
            return;
        }
        appendText(sb, text);
        if (StringUtils.isNotBlank(href)) {
            sb.append(LINK_OPEN).append(href).append(LINK_CLOSE);
        }
    }

    private static void appendText(StringBuilder sb, String text) {
        if (StringUtils.isNotBlank(text)) {
            sb.append(text.replace(NBSP, SPACE));
        }
    }

    private static void ensureBlankLine(StringBuilder sb) {
        if (sb.isEmpty() || sb.toString().endsWith(PARAGRAPH)) {
            return;
        }
        if (!sb.toString().endsWith(LINE)) {
            sb.append(LINE);
        }
        sb.append(LINE);
    }

    private static String normalize(String value) {
        StringBuilder normalized = new StringBuilder();
        for (String line : value.replace(CARRIAGE_RETURN, StringUtils.EMPTY).split(LINE, -1)) {
            normalized.append(StringUtils.stripEnd(line, null)).append(LINE);
        }
        return EXTRA_BLANK_LINES.matcher(normalized.toString()).replaceAll(PARAGRAPH).trim();
    }
}
