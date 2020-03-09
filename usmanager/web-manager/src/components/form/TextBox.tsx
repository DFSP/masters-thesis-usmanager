import React from "react";
import {getTypeFromValue} from "./Field";

interface TextBoxProps {
  className: string;
  id: string;
  name: string;
  value?: any;
  disabled?: boolean;
  onChange: (e: React.FormEvent<HTMLInputElement>) => void;
  onBlur: (e: React.FormEvent<HTMLInputElement>) => void;
}

export class TextBox extends React.Component<TextBoxProps, any> {

  render(): any {
    const {className, id, name, value, disabled, onChange, onBlur} = this.props;
    return (
      <input
        className={className}
        id={id}
        name={name}
        type={getTypeFromValue(value)}
        value={value}
        disabled={disabled}
        autoComplete="off"
        onChange={onChange}
        onBlur={onBlur}
        formNoValidate={true}/>
    )
  }

}
