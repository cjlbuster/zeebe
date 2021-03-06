/*
 * Zeebe Broker Core
 * Copyright © 2017 camunda services GmbH (info@camunda.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package io.zeebe.broker.workflow.graph.transformer.validator;

import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.INPUT_MAPPING_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.IO_MAPPING_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.MAPPING_ATTRIBUTE_SOURCE;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.MAPPING_ATTRIBUTE_TARGET;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.OUTPUT_MAPPING_ELEMENT;
import static io.zeebe.broker.workflow.graph.transformer.ZeebeExtensions.ZEEBE_NAMESPACE;
import static io.zeebe.broker.workflow.graph.transformer.validator.ValidationCodes.INVALID_JSON_PATH_EXPRESSION;
import static io.zeebe.broker.workflow.graph.transformer.validator.ValidationCodes.PROHIBITED_JSON_PATH_EXPRESSION;
import static io.zeebe.broker.workflow.graph.transformer.validator.ValidationCodes.REDUNDANT_MAPPING;

import java.util.List;
import java.util.regex.Pattern;

import org.agrona.Strings;
import org.camunda.bpm.model.bpmn.instance.ExtensionElements;
import org.camunda.bpm.model.xml.instance.DomElement;
import org.camunda.bpm.model.xml.instance.ModelElementInstance;
import org.camunda.bpm.model.xml.validation.ModelElementValidator;
import org.camunda.bpm.model.xml.validation.ValidationResultCollector;

import io.zeebe.msgpack.jsonpath.JsonPathQuery;
import io.zeebe.msgpack.jsonpath.JsonPathQueryCompiler;
import io.zeebe.msgpack.mapping.Mapping;

/**
 * Represents the IO mapping validation rule.
 */
public class IOMappingRule implements ModelElementValidator<ExtensionElements>
{
    public static final String ERROR_MSG_PROHIBITED_EXPRESSION = "Mapping failed! JSON Path contains prohibited expression (for example $.* or $.(foo|bar)).";
    public static final String ERROR_MSG_INVALID_EXPRESSION = "Mapping failed JSON Path Query is not valid! Reason: %s";
    public static final String ERROR_MSG_REDUNDANT_MAPPING = "Mapping failed! If Root path is mapped other mapping (makes no sense) is disallowed.";

    private static final String PROHIBITED_EXPRESSIONS_REGEX = "(\\.\\*)|(\\[.*,.*\\])";
    private static final Pattern PROHIBITED_EXPRESSIONS = Pattern.compile(PROHIBITED_EXPRESSIONS_REGEX);

    @Override
    public Class<ExtensionElements> getElementType()
    {
        return ExtensionElements.class;
    }

    @Override
    public void validate(ExtensionElements extensionElements, ValidationResultCollector validationResultCollector)
    {
        final ModelElementInstance ioMappingElement = extensionElements.getUniqueChildElementByNameNs(ZEEBE_NAMESPACE, IO_MAPPING_ELEMENT);

        if (ioMappingElement != null)
        {
            final DomElement domElement = ioMappingElement.getDomElement();
            final List<DomElement> inputMappingElements = domElement.getChildElementsByNameNs(ZEEBE_NAMESPACE, INPUT_MAPPING_ELEMENT);
            final List<DomElement> outputMappingElements = domElement.getChildElementsByNameNs(ZEEBE_NAMESPACE, OUTPUT_MAPPING_ELEMENT);

            validateMappings(validationResultCollector, inputMappingElements);
            validateMappings(validationResultCollector, outputMappingElements);
        }
    }

    private static void validateMappings(ValidationResultCollector validationResultCollector, List<DomElement> mappingElements)
    {
        if (mappingElements != null && !mappingElements.isEmpty())
        {
            for (int i = 0; i < mappingElements.size(); i++)
            {
                final DomElement mapping = mappingElements.get(i);

                validateMappingExpression(validationResultCollector, mapping, MAPPING_ATTRIBUTE_SOURCE);
                final boolean isRootMapping = validateMappingExpression(validationResultCollector, mapping, MAPPING_ATTRIBUTE_TARGET);

                if (isRootMapping && mappingElements.size() > 1)
                {
                    validationResultCollector.addError(REDUNDANT_MAPPING, ERROR_MSG_REDUNDANT_MAPPING);
                }
            }
        }
    }

    private static boolean validateMappingExpression(ValidationResultCollector validationResultCollector, DomElement mappingElement, String attributeName)
    {
        boolean isRootMapping = false;

        if (mappingElement != null)
        {
            final String mapping = mappingElement.getAttribute(attributeName);
            if (!Strings.isEmpty(mapping))
            {
                if (PROHIBITED_EXPRESSIONS.matcher(mapping).find())
                {
                    validationResultCollector.addError(PROHIBITED_JSON_PATH_EXPRESSION, ERROR_MSG_PROHIBITED_EXPRESSION);
                }

                if (mapping.equals(Mapping.JSON_ROOT_PATH))
                {
                    isRootMapping = true;
                }

                final JsonPathQuery jsonPathQuery = new JsonPathQueryCompiler().compile(mapping);
                if (!jsonPathQuery.isValid())
                {
                    validationResultCollector.addError(INVALID_JSON_PATH_EXPRESSION,
                        String.format(ERROR_MSG_INVALID_EXPRESSION, jsonPathQuery.getErrorReason()));
                }
            }
        }

        return isRootMapping;
    }
}
