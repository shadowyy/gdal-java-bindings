package ch.so.agi.gdal.ffm.internal;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Parses GDAL creation-option-list XML into enumerated option values.
 * <p>
 * 解析 GDAL 创建选项列表 XML 并提取枚举取值的内部工具类，用于查询驱动支持的选项值。
 * <p>
 * Internal API, not public. Do not use from application code; it may change without notice.
 * 内部 API，非公开接口，请勿在业务代码中直接使用，后续可能随时变更。
 */
final class CreationOptionListParser {
    /**
     * Prevents instantiation of this utility class.
     * <p>
     * 禁止实例化的私有构造器，本类仅提供静态解析方法。
     */
    private CreationOptionListParser() {
    }

    /**
     * Collects the distinct enum values of one named option, preserving document order.
     * <p>
     * 按文档顺序收集指定选项名的去重枚举值，匹配时忽略大小写与首尾空白。
     *
     * @param creationOptionListXml creation-option-list XML, may be {@code null} or blank (yields empty list) /
     *                              创建选项列表 XML，可为 {@code null} 或空白（此时返回空列表）
     * @param optionName            option name to look up, must not be {@code null} / 待查找的选项名，不能为 {@code null}
     * @return distinct non-blank values in document order, never {@code null} / 按文档顺序去重的非空取值，永不为 {@code null}
     * @throws NullPointerException if {@code optionName} is {@code null} / {@code optionName} 为 {@code null} 时抛出
     */
    static List<String> enumValues(String creationOptionListXml, String optionName) {
        Objects.requireNonNull(optionName, "optionName must not be null");
        String normalizedOptionName = optionName.trim();
        if (normalizedOptionName.isEmpty() || creationOptionListXml == null || creationOptionListXml.isBlank()) {
            return List.of();
        }

        Document document = parse(creationOptionListXml);
        if (document == null) {
            return List.of();
        }

        LinkedHashSet<String> values = new LinkedHashSet<>();
        NodeList optionNodes = document.getElementsByTagName("Option");
        for (int i = 0; i < optionNodes.getLength(); i++) {
            Node optionNode = optionNodes.item(i);
            if (!(optionNode instanceof Element optionElement)) {
                continue;
            }
            if (!normalizedOptionName.equalsIgnoreCase(optionElement.getAttribute("name"))) {
                continue;
            }

            NodeList valueNodes = optionElement.getElementsByTagName("Value");
            for (int valueIndex = 0; valueIndex < valueNodes.getLength(); valueIndex++) {
                Node valueNode = valueNodes.item(valueIndex);
                if (!(valueNode instanceof Element valueElement)) {
                    continue;
                }

                String value = valueElement.getTextContent();
                if (value == null || value.isBlank()) {
                    value = valueElement.getAttribute("value");
                }
                if (value != null) {
                    String normalized = value.trim();
                    if (!normalized.isEmpty()) {
                        values.add(normalized);
                    }
                }
            }
        }
        return List.copyOf(values);
    }

    /**
     * Parses XML text into a DOM document with secure-processing hardening.
     * <p>
     * 将 XML 文本解析为 DOM 文档，启用安全处理并禁用外部实体，解析失败返回 {@code null}。
     *
     * @param xml XML text to parse, must not be {@code null} / 待解析的 XML 文本，不能为 {@code null}
     * @return parsed document, or {@code null} when parsing fails / 解析后的文档，解析失败时返回 {@code null}
     */
    private static Document parse(String xml) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(false);
            factory.setXIncludeAware(false);
            setFeatureIfSupported(factory, XMLConstants.FEATURE_SECURE_PROCESSING, true);
            setFeatureIfSupported(factory, "http://apache.org/xml/features/disallow-doctype-decl", true);
            setFeatureIfSupported(factory, "http://xml.org/sax/features/external-general-entities", false);
            setFeatureIfSupported(factory, "http://xml.org/sax/features/external-parameter-entities", false);
            setFeatureIfSupported(factory, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false);

            DocumentBuilder builder = factory.newDocumentBuilder();
            return builder.parse(new InputSource(new StringReader(xml)));
        } catch (Exception ignored) {
            return null;
        }
    }

    /**
     * Enables a parser feature when the implementation supports it.
     * <p>
     * 当实现支持时启用指定的解析器特性，不支持时保持默认行为并忽略异常。
     *
     * @param factory document builder factory, must not be {@code null} / 文档构建器工厂，不能为 {@code null}
     * @param feature feature URI to set, must not be {@code null} / 待设置的特性 URI，不能为 {@code null}
     * @param enabled {@code true} to enable, {@code false} to disable / {@code true} 表示启用，{@code false} 表示禁用
     */
    private static void setFeatureIfSupported(DocumentBuilderFactory factory, String feature, boolean enabled) {
        try {
            factory.setFeature(feature, enabled);
        } catch (ParserConfigurationException ignored) {
            // Use default parser behavior for unsupported features.
        }
    }
}
