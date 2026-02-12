import type { ReactNode } from "react";

export default function DialogFrame({
  title,
  children,
  onClose,
}: {
  title: string;
  children: ReactNode;
  onClose: () => void;
}) {
  return (
    <div className="modal-backdrop" role="dialog" aria-modal="true">
      <div className="modal">
        <div className="modal-header">
          <div className="strong">{title}</div>
          <button className="btn" onClick={onClose}>
            关闭
          </button>
        </div>
        <div className="modal-body">{children}</div>
      </div>
    </div>
  );
}

