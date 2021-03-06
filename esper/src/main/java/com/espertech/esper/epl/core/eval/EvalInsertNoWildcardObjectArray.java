/*
 ***************************************************************************************
 *  Copyright (C) 2006 EsperTech, Inc. All rights reserved.                            *
 *  http://www.espertech.com/esper                                                     *
 *  http://www.espertech.com                                                           *
 *  ---------------------------------------------------------------------------------- *
 *  The software in this package is published under the terms of the GPL license       *
 *  a copy of which has been included with this distribution in the license.txt file.  *
 ***************************************************************************************
 */
package com.espertech.esper.epl.core.eval;

import com.espertech.esper.client.EventBean;
import com.espertech.esper.client.EventType;
import com.espertech.esper.epl.core.SelectExprProcessor;
import com.espertech.esper.epl.expression.core.ExprEvaluatorContext;

public class EvalInsertNoWildcardObjectArray extends EvalBase implements SelectExprProcessor {

    public EvalInsertNoWildcardObjectArray(SelectExprContext selectExprContext, EventType resultEventType) {
        super(selectExprContext, resultEventType);
    }

    public EventBean process(EventBean[] eventsPerStream, boolean isNewData, boolean isSynthesize, ExprEvaluatorContext exprEvaluatorContext) {
        Object[] result = new Object[super.getExprNodes().length];
        for (int i = 0; i < super.getExprNodes().length; i++) {
            result[i] = super.getExprNodes()[i].evaluate(eventsPerStream, isNewData, exprEvaluatorContext);
        }

        return super.getEventAdapterService().adapterForTypedObjectArray(result, super.getResultEventType());
    }
}
