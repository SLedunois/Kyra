import React, {useEffect} from 'react';
import {createPortal} from "react-dom";

import {Close} from '@ui/icons';
import {Button} from "@ui/Button";

type IModal = {
  active: boolean
  title: string
  onClose: any
  onValidation: any
  children?: any
}

type IModalHeader = {
  title: string
  onClose: any
}

type IModalFooter = {
  validationLabel: string
  onValidation: any
  onClose: any
}

const ModalHeader = ({title, onClose}: IModalHeader) => (
  <div className="flex justify-between items-center p-4">
    <p className="text-xl font-regular">{title}</p>
    <div className="modal-close cursor-pointer z-50">
      <Close className="fill-grey w-3 h-3" onClick={onClose}/>
    </div>
  </div>
)

const ModalFooter = ({onClose, onValidation, validationLabel}: IModalFooter) => (
  <div className="flex justify-between p-4">
    <Button onClick={onClose} label="Cancel" cancel={true}/>
    <Button onClick={onValidation} label={validationLabel}/>
  </div>
);

const ModalComponent = ({title, children, active, onClose, onValidation}: IModal) => {
  const onEsc = (evt: KeyboardEvent) => {
    let isEscape = (evt.key === "Escape" || evt.key === "Esc");
    if (isEscape) onClose();
  }
  useEffect(() => {
    document.addEventListener('keydown', onEsc);
    return () => document.removeEventListener('keydown', onEsc)
  });

  return (
    <div className={`transition duration-300 ease-in-out fixed w-full h-full top-0 left-0 flex items-center
    justify-center ${active ? 'opacity-1' : 'opacity-0 pointer-events-none'}`}>
      <div className="modal-overlay absolute w-full h-full bg-black opacity-50"
           onClick={onClose}>&nbsp;</div>
      <div
        className="modal-container bg-white w-11/12 md:max-w-md mx-auto rounded z-50 overflow-y-auto min-w-tier">
        <div className="modal-content text-left">
          <ModalHeader title={title} onClose={onClose}/>
          <div className="bg-background p-4">
            {...children}
          </div>
          <ModalFooter
            validationLabel={"add"}
            onValidation={onValidation}
            onClose={onClose}/>
        </div>
      </div>
    </div>
  )
}

export const Modal = (props: IModal) => {
  return createPortal(
    <ModalComponent {...props} />,
    document.getElementById("modal")
  )
};