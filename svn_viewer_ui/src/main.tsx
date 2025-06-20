import { render } from "preact";
import "./index.css";
import Notifications from "./wrappers/notifications";

render(<Notifications />, document.getElementById("app")!);
