/*
 * MIT License
 *
 * Copyright (c) 2020 manager
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

import React from "react";
import {mapLabelToMaterialIcon} from "../../utils/image";
import {camelCaseToSentenceCase} from "../../utils/text";
import M from "materialize-css";
import {FormContext, IErrors, IFormContext, IValues} from "./Form";
import {TextBox} from "./TextBox";
import {MultilineTextBox} from "./MultilineTextBox";
import {Datepicker} from "./Datepicker";
import {Timepicker} from "./Timepicker";
import {NumberBox} from "./NumberBox";
import {Dropdown} from "./Dropdown";
import {CheckboxList} from "./CheckboxList";
import checkboxListStyles from "./CheckboxList.module.css";
import {Link} from "react-router-dom";
import LocationMap from "../map/LocationMap";
import {IMarker} from "../map/Marker";

export interface IValidation {
    rule: (values: IValues, id: keyof IValues, args: any) => string;
    args?: any;
}

export interface FieldProps<T = string> {
    id: string;
    type?: "text" | "number" | "date" | "datepicker" | "timepicker" | "multilinetext" | "dropdown" | "list" | "map";
    label?: string;
    value?: any;
    valueToString?: (v: T) => string;
    dropdown?: { defaultValue: string, values: T[], optionToString?: (v: T) => string, selectCallback?: (value: any) => void, emptyMessage?: string };
    number?: { min: number, max: number };
    validation?: IValidation;
    icon?: { include?: boolean, name?: string, linkedTo?: ((v: T) => string | null) | string };
    disabled?: boolean;
    hidden?: boolean;
    map?: { editable?: boolean, singleMarker?: boolean, zoomable?: boolean, labeled?: boolean, markers?: IMarker[] }
}

export const getTypeFromValue = (value: any): 'text' | 'number' =>
    value === undefined
    || value === ''
    || typeof value === 'boolean'
    || (typeof value === 'string' && !value.trim().length)
    || isNaN(value)
        ? 'text'
        : 'number';

export default class Field<T> extends React.Component<FieldProps<T>, {}> {

    public componentDidMount(): void {
        this.updateField();
    }

    public componentDidUpdate(prevProps: Readonly<FieldProps<T>>, prevState: Readonly<{}>, snapshot?: any): void {
        this.updateField();
    }

    private linkedIcon = (label: string, iconName: string | undefined, linkedTo: ((v: T) => string | null) | string, value: any, valueToString: ((v: T) => string) | undefined): JSX.Element => {
        const icon = <>{iconName ? iconName : mapLabelToMaterialIcon(label, value)}</>;
        const valueString = value === undefined ? '' : (valueToString ? valueToString(value) : value);
        let link;
        if (typeof linkedTo === 'string') {
            link = `${linkedTo}/${valueString}`;
        } else if (linkedTo(value) !== null) {
            link = `${linkedTo(value)}/${valueString}`;
        } else {
            return icon;
        }
        return <Link to={link}>{icon}</Link>;
    }

    public render() {
        const {id, type, label, dropdown, number, icon, disabled, hidden, valueToString, map} = this.props;
        const getError = (errors: IErrors): string => (errors ? errors[id] : "");
        const getEditorClassname = (errors: IErrors, disabled: boolean, value: string): string => {
            const hasErrors = getError(errors);
            if (hasErrors) {
                return "invalidate-field";
            } else if (!hasErrors && !disabled && (getTypeFromValue(value) !== 'text' || value)) {
                return "validate-field";
            } else {
                return "no-validation-field";
            }
        };
        return (
            <FormContext.Consumer>
                {(formContext: IFormContext | null) => (
                    formContext && (
                        <div key={id}
                             className={type !== 'list' ? `input-field col s12` : `input-field col s12 ${checkboxListStyles.listWrapper}`}
                             style={icon?.include !== undefined && !icon?.include ? {marginLeft: '10px'} : undefined}>
                            {label && type !== "list" && (
                                <>
                                    {(icon?.include === undefined || icon?.include) &&
                                    <i className="material-icons prefix">
                                        {icon?.linkedTo
                                            ? this.linkedIcon(label, icon.name, icon.linkedTo, formContext.values[id], valueToString)
                                            : icon?.name ? icon.name : mapLabelToMaterialIcon(label, formContext.values[id])}
                                    </i>}
                                    <label className="active" htmlFor={id}>
                                        {camelCaseToSentenceCase(label)}
                                    </label>
                                </>
                            )}
                            {(!type || type.toLowerCase() === "text") && (
                                <TextBox<T>
                                    className={getEditorClassname(formContext.errors, !formContext.isEditing, formContext.values[id])}
                                    id={id}
                                    name={id}
                                    value={formContext.values[id]}
                                    disabled={disabled || !formContext.isEditing}
                                    onChange={this.onChange(id, formContext, !!formContext.errors[id])}
                                    valueToString={valueToString}
                                    onBlur={this.onBlur(id, formContext)}
                                    hidden={hidden}/>
                            )}
                            {type && type.toLowerCase() === "number" && (
                                <NumberBox
                                    className={getEditorClassname(formContext.errors, !formContext.isEditing, formContext.values[id])}
                                    id={id}
                                    name={id}
                                    value={formContext.values[id]}
                                    min={number && number.min}
                                    max={number && number.max}
                                    disabled={disabled || !formContext.isEditing}
                                    onChange={this.onChange(id, formContext, !!formContext.errors[id])}
                                    onBlur={this.onBlur(id, formContext)}/>
                            )}
                            {type && type.toLowerCase() === "date" && (
                                <TextBox
                                    className={getEditorClassname(formContext.errors, !formContext.isEditing, formContext.values[id])}
                                    id={id}
                                    name={id}
                                    value={this.getDateStringFromTimestamp(formContext.values[id])}
                                    disabled={disabled || !formContext.isEditing}
                                    onChange={this.onChange(id, formContext, !!formContext.errors[id])}
                                    onBlur={this.onBlur(id, formContext)}/>
                            )}
                            {(type && type.toLowerCase() === "datepicker") && (
                                <Datepicker
                                    className={getEditorClassname(formContext.errors, !formContext.isEditing, formContext.values[id])}
                                    id={id}
                                    name={id}
                                    value={formContext.values[id]}
                                    disabled={disabled || !formContext.isEditing}
                                    onSelect={this.onSelect(id, formContext)}
                                    onChange={this.onChange(id, formContext, !!formContext.errors[id])}/>
                            )}
                            {(type && type.toLowerCase() === "timepicker") && (
                                <Timepicker
                                    className={getEditorClassname(formContext.errors, !formContext.isEditing, formContext.values[id])}
                                    id={id}
                                    name={id}
                                    value={formContext.values[id]}
                                    disabled={disabled || !formContext?.isEditing}
                                    onSelect={this.onSelect(id, formContext)}
                                    onChange={this.onChange(id, formContext, !!formContext.errors[id])}/>
                            )}
                            {type && type.toLowerCase() === "multilinetext" && (
                                <MultilineTextBox
                                    className={getEditorClassname(formContext.errors, !formContext.isEditing, formContext.values[id])}
                                    id={id}
                                    name={id}
                                    value={formContext.values[id]}
                                    disabled={disabled || !formContext.isEditing}
                                    onChange={this.onChange(id, formContext, !!formContext.errors[id])}
                                    onBlur={this.onBlur(id, formContext)}/>
                            )}
                            {type && type.toLowerCase() === "dropdown" && dropdown && (
                                <Dropdown
                                    className={getEditorClassname(formContext.errors, !formContext.isEditing, formContext.values[id])}
                                    id={id}
                                    name={id}
                                    value={formContext.values[id]}
                                    disabled={disabled || !formContext.isEditing}
                                    onChange={this.onChange(id, formContext, !!formContext.errors[id], true)}
                                    onBlur={this.onBlur(id, formContext)}
                                    dropdown={dropdown}/>
                            )}
                            {(type && type.toLowerCase() === "list") && (
                                <CheckboxList id={id}
                                              name={id}
                                              values={this.props.value}
                                              disabled={disabled || !formContext?.isEditing}
                                              onCheck={(listId, itemId, checked) => this.onCheck(listId, itemId, checked, formContext)}/>
                            )}
                            {(type && type.toLowerCase() === "map") && (
                                <LocationMap
                                    onSelect={(map?.editable && formContext.isEditing) ? this.onSelectCoordinates(id, formContext) : undefined}
                                    onDeselect={(map?.editable && formContext.isEditing) ? this.onDeselectCoordinates(id, formContext) : undefined}
                                    locations={formContext.values[id] ? (Array.isArray(formContext.values[id]) ? formContext.values[id].concat(map?.markers || []) : [formContext.values[id]].concat(map?.markers || [])) : map?.markers || []}
                                    marker={{size: 5, labeled: map?.labeled}} hover clickHighlight
                                    zoomable={!map?.editable || (map?.zoomable && !formContext.isEditing)}
                                    resizable/>
                            )}
                            {getError(formContext.errors) && (
                                <span className="helper-text red-text darken-3">
                    {getError(formContext.errors)}
                  </span>
                            )}
                        </div>
                    )
                )}
            </FormContext.Consumer>
        )
    }

    private updateField = () =>
        M.updateTextFields();

    private onChange = (id: string, formContext: IFormContext, validate: boolean, selected?: boolean) => (e: React.FormEvent<HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement>): void => {
        const target = e.target as HTMLInputElement | HTMLTextAreaElement | HTMLSelectElement;
        let value = target.value;
        if (!isNaN(+value)) {
            value = `"${value}"`;
        }
        try {
            value = JSON.parse(value);
        } catch (_) {
        }
        if (selected) {
            this.props.dropdown?.selectCallback?.(value);
        }
        formContext.setValue(id, value, validate);
    };

    private onSelect = (id: string, formContext: IFormContext) => (value: string) => {
        formContext.setValue(id, value);
    };

    private onBlur = (id: string, formContext: IFormContext) => (): void =>
        formContext.validate(id);

    private onCheck = (listId: keyof IValues, itemId: string, checked: boolean, formContext: IFormContext) => {
        if (checked) {
            formContext.addValue(listId, itemId);
        } else {
            formContext.removeValue(listId, itemId);
        }
    };

    private onSelectCoordinates = (id: string, formContext: any) => (marker: IMarker): void => {
        if (this.props.map?.singleMarker) {
            formContext.setValue(id, marker, false);
        } else {
            formContext.addValue(id, marker);
        }
    }

    private onDeselectCoordinates = (id: string, formContext: any) => (marker: IMarker): void =>
        formContext.removeValue(id, marker);

    private getDateStringFromTimestamp = (value: number) => {
        const date = new Date(value * 1000);
        return `${date.toLocaleDateString("pt")} ${date.toLocaleTimeString("pt")}`
    };

}