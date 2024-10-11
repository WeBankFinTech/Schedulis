package azkaban.utils;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

public class XmlResolveUtils {

    /**
     * 防止 XML External Entity (XXE) 攻击
     * @param docBuilderFactory
     * @throws ParserConfigurationException
     */
    public static void avoidXEE(DocumentBuilderFactory docBuilderFactory) throws ParserConfigurationException {

        // 防止 XML External Entity (XXE) 攻击
        // 1. 如果需要禁用DOCTYPE ，可以阻止大部分的XML External Entity (XXE)
        // String feature1 = "http://apache.org/xml/features/disallow-doctype-decl";
        // docBuilderFactory.setFeature(feature1, true);

        // 2. 如果不禁用DOCTYPE, 则使用下面的设置
        String feature2 = "http://xml.org/sax/features/external-general-entities";
        String feature3 = "http://xml.org/sax/features/external-parameter-entities";
        String feature4 = "http://apache.org/xml/features/nonvalidating/load-external-dtd";

        docBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
        docBuilderFactory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
        docBuilderFactory.setFeature(feature2, false);
        docBuilderFactory.setFeature(feature3, false);
        docBuilderFactory.setFeature(feature4, false);
        docBuilderFactory.setXIncludeAware(false);
        docBuilderFactory.setExpandEntityReferences(false);
    }
}
