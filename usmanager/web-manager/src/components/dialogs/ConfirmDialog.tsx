import React, {createRef} from "react";
import M from "materialize-css";

interface Props {
  message: string;
  cancel?: boolean;
  cancelCallback?: () => void;
  confirm?: boolean;
  confirmCallback?: () => void;
}

export default class ConfirmDialog extends React.Component<Props, {}> {

  private modal = createRef<HTMLDivElement>();

  componentDidUpdate(prevProps: Readonly<Props>, prevState: Readonly<{}>, snapshot?: any): void {
    M.Modal.init(this.modal.current as Element, { startingTop: '38.5%', endingTop: '38.5%', preventScrolling: false });
  }

  render() {
    return (
      <div id="confirm-dialog" className='modal dialog' ref={this.modal}>
        <div className="modal-content">
          <div className="modal-message">Are you sure you want to <div className={`dialog-message`}>{this.props.message}?</div></div>
        </div>
        <div className={`modal-footer dialog-footer`}>
          <button className="modal-close waves-effect waves-red btn-flat white-text"
                  onClick={this.props.cancelCallback}>
            No
          </button>
          <button className="modal-close waves-effect waves-green btn-flat white-text"
                  onClick={this.props.confirmCallback}>
            Absolutely
          </button>
        </div>
      </div>
    );
  }

}