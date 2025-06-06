// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.hypervisor.kvm.resource;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.StringReader;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.cloudstack.utils.security.ParserUtils;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

public class LibvirtXMLParser extends DefaultHandler {
    protected Logger logger = LogManager.getLogger(getClass());
    protected static final SAXParserFactory s_spf;
    static {
        s_spf = ParserUtils.getSaferSAXParserFactory();
    }
    protected SAXParser _sp;
    protected boolean _initialized = false;

    public LibvirtXMLParser() {
        try {
            _sp = s_spf.newSAXParser();
            _initialized = true;
        } catch (ParserConfigurationException e) {
            logger.trace("Ignoring xml parser error.", e);
        } catch (SAXException e) {
            logger.trace("Ignoring xml parser error.", e);
        }
    }

    public boolean parseDomainXML(String domXML) {
        if (!_initialized) {
            return false;
        }
        try {
            _sp.parse(new InputSource(new StringReader(domXML)), this);
            return true;
        } catch (SAXException se) {
            logger.warn(se.getMessage());
        } catch (IOException ie) {
            logger.error(ie.getMessage());
        }
        return false;
    }

    public static String getXml(Document doc) throws TransformerException {
        TransformerFactory transformerFactory = ParserUtils.getSaferTransformerFactory();
        Transformer transformer = transformerFactory.newTransformer();

        DOMSource source = new DOMSource(doc);

        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        StreamResult result = new StreamResult(byteArrayOutputStream);

        transformer.transform(source, result);

        return byteArrayOutputStream.toString();
    }

    @Override
    public void characters(char[] ch, int start, int length) throws SAXException {
    }

}
