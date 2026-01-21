import "./index.css";
import { render } from "preact";
import Notifications from "./wrappers/notifications";

render(<Notifications />, document.getElementById("app")!);
