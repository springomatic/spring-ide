/*
 * Copyright 2002-2005 the original author or authors.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ide.eclipse.web.flow.core.internal.model;

import java.util.Iterator;

import org.springframework.ide.eclipse.web.flow.core.model.IAttributeMapper;
import org.springframework.ide.eclipse.web.flow.core.model.ICloneableModelElement;
import org.springframework.ide.eclipse.web.flow.core.model.IModelWriter;
import org.springframework.ide.eclipse.web.flow.core.model.IPersistableModelElement;
import org.springframework.ide.eclipse.web.flow.core.model.IProperty;
import org.springframework.ide.eclipse.web.flow.core.model.ISetup;
import org.springframework.ide.eclipse.web.flow.core.model.IViewState;
import org.springframework.ide.eclipse.web.flow.core.model.IWebFlowModelElement;
import org.springframework.ide.eclipse.web.flow.core.model.IWebFlowState;

public class ViewState extends AbstractTransitionableFrom implements
        IViewState, IPersistableModelElement, ICloneableModelElement {

    private String view;
    
    private ISetup setup;

    public ViewState(IWebFlowModelElement parent, String id, String viewName) {
        super(parent, id);
        this.view = viewName;
        if (parent instanceof IWebFlowState) {
            ((IWebFlowState) parent).addState(this);
        }
    }

    public ViewState(IWebFlowModelElement parent, String id) {
        this(parent, id, null);
    }

    public ViewState() {
        this(null, null, null);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.ide.eclipse.web.core.model.IViewState#getView()
     */
    public String getView() {
        return this.view;
    }

    /**
     * @param view
     *            The view to set.
     */
    public void setView(String view) {
        String oldValue = this.view;
        this.view = view;
        super.firePropertyChange(PROPS, oldValue, view);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.ide.eclipse.web.flow.core.internal.model.WebFlowModelElement#getElementType()
     */
    public int getElementType() {
        return IWebFlowModelElement.VIEW_STATE;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.ide.eclipse.web.flow.core.model.IPersistable#save(org.springframework.ide.eclipse.web.flow.core.model.IModelWriter)
     */
    public void save(IModelWriter writer) {
        writer.doStart(this);

        if (this.setup != null) {
            ((IPersistableModelElement) this.setup).save(writer);
        }
        super.save(writer);
        Iterator iter = this.getProperties().iterator();
        while (iter.hasNext()) {
            IWebFlowModelElement element = (IWebFlowModelElement) iter.next();
            if (element instanceof IPersistableModelElement) {
                ((IPersistableModelElement) element).save(writer);
            }
        }         
        writer.doEnd(this);
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.ide.eclipse.web.flow.core.model.IConeableModelElement#cloneModelElement()
     */
    public ICloneableModelElement cloneModelElement() {
        ViewState state = new ViewState();
        state.setId(getId());
        state.setView(getView());
        state.setAutowire(getAutowire());
        state.setBean(getBean());
        state.setBeanClass(getBeanClass());
        state.setClassRef(getClassRef());
        state.setElementName(getElementName());
        for (int i = 0; i < this.getProperties().size(); i++) {
            Property property = (Property) this.getProperties().get(i);
            state.addProperty((IProperty) property.cloneModelElement());
        }
        if (this.setup != null) {
            state
                    .setSetup((ISetup) ((ICloneableModelElement) this.setup)
                            .cloneModelElement());
        }
        return state;
    }

    /*
     * (non-Javadoc)
     * 
     * @see org.springframework.ide.eclipse.web.flow.core.model.IConeableModelElement#applyCloneValues(org.springframework.ide.eclipse.web.flow.core.model.IConeableModelElement)
     */
    public void applyCloneValues(ICloneableModelElement element) {
        if (element instanceof IViewState) {
            IViewState state = (IViewState) element;
            setView(state.getView());
            setId(state.getId());
            setAutowire(state.getAutowire());
            setBean(state.getBean());
            setBeanClass(state.getBeanClass());
            setClassRef(state.getClassRef());
            Property[] props = (Property[]) this.getProperties().toArray(
                    new Property[this.getProperties().size()]);
            for (int i = 0; i < props.length; i++) {
                removeProperty(props[i]);
            }
            for (int i = 0; i < state.getProperties().size(); i++) {
                addProperty((IProperty) state.getProperties().get(i));
            }
            if (state.getSetup() != null) {
                if (this.setup != null) {
                    ((ICloneableModelElement) this.setup)
                            .applyCloneValues((ICloneableModelElement) state
                                    .getSetup());
                }
                else {
                    setSetup(state.getSetup());
                    getSetup().setElementParent(this);
                }
            }
            else {
                if (this.setup != null) {
                    removeSetup();
                }
            }
        }
    }

    /* (non-Javadoc)
     * @see org.springframework.ide.eclipse.web.flow.core.model.IViewState#getSetup()
     */
    public ISetup getSetup() {
        return this.setup;
    }

    /* (non-Javadoc)
     * @see org.springframework.ide.eclipse.web.flow.core.model.IViewState#setSetup(org.springframework.ide.eclipse.web.flow.core.model.ISetup)
     */
    public void setSetup(ISetup setup) {
        //if (this.getSetup() != null) {
        //    this.removeSetup();
        //}
        this.setup = setup;
        super.firePropertyChange(ADD_CHILDREN, new Integer(0), setup);
    }

    /* (non-Javadoc)
     * @see org.springframework.ide.eclipse.web.flow.core.model.IViewState#removeSetup()
     */
    public void removeSetup() {
        ISetup oldValue = this.setup;
        this.setup = null;
        super.firePropertyChange(REMOVE_CHILDREN, setup, oldValue);
        
    }
}