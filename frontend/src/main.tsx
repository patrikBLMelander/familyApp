import React from "react";
import ReactDOM from "react-dom/client";
import { App } from "./App";
import { installPaymentRequiredNotice } from "./shared/api/paymentRequiredNotice";
import { installWebLanguageHeader } from "./shared/api/webLanguage";
import "./styles.css";

installWebLanguageHeader();
installPaymentRequiredNotice();

ReactDOM.createRoot(document.getElementById("root") as HTMLElement).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>
);



