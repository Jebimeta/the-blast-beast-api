package com.theblastbeast.service;

import java.util.List;
import org.commonmark.Extension;
import org.commonmark.ext.autolink.AutolinkExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.Node;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;
import org.owasp.html.HtmlPolicyBuilder;
import org.owasp.html.PolicyFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Renderiza Markdown a HTML y lo sanea con una lista blanca de etiquetas.
 * El HTML resultante es el único que se sirve al público.
 */
@Service
public class MarkdownService {

    private static final PolicyFactory POLICY = new HtmlPolicyBuilder()
            .allowElements(
                    "p", "br", "hr",
                    "h1", "h2", "h3", "h4", "h5", "h6",
                    "blockquote", "ul", "ol", "li",
                    "strong", "em", "del", "code", "pre",
                    "a", "img",
                    "table", "thead", "tbody", "tr", "th", "td",
                    "figure", "figcaption")
            .allowAttributes("href").onElements("a")
            .allowAttributes("src", "alt", "title", "width", "height").onElements("img")
            .allowAttributes("class").onElements("code", "pre", "table", "th", "td")
            .allowAttributes("colspan", "rowspan").onElements("th", "td")
            .allowUrlProtocols("http", "https", "mailto")
            .requireRelNofollowOnLinks()
            .toFactory();

    private static final List<Extension> EXTENSIONS = List.of(
            TablesExtension.create(),
            AutolinkExtension.create());

    private static final Parser PARSER = Parser.builder()
            .extensions(EXTENSIONS)
            .build();

    private static final HtmlRenderer RENDERER = HtmlRenderer.builder()
            .extensions(EXTENSIONS)
            // El HTML crudo se permite y se sanea después con la lista blanca:
            // así la redacción puede usar <figure>/<img> sin abrir la puerta a scripts.
            .escapeHtml(false)
            .build();

    public String render(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return "";
        }
        Node document = PARSER.parse(markdown);
        return POLICY.sanitize(RENDERER.render(document));
    }

    public int readingMinutes(String markdown) {
        if (!StringUtils.hasText(markdown)) {
            return 0;
        }
        String plain = markdown
                .replaceAll("(?s)```.*?```", " ")
                .replaceAll("(?s)<[^>]+>", " ")
                .replaceAll("!\\[[^]]*]\\([^)]*\\)", " ")
                .replaceAll("\\[([^]]*)]\\([^)]*\\)", "$1")
                .replaceAll("[#>*_`~\\-]", " ");
        long words = List.of(plain.trim().split("\\s+")).stream()
                .filter(StringUtils::hasText)
                .count();
        return words == 0 ? 0 : (int) Math.max(1, Math.ceil(words / 200.0));
    }
}
