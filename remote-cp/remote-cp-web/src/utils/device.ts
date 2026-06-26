export function detectDeviceType(): "Phone" | "Tablet" | "Computer" {
  const userAgent = navigator.userAgent.toLowerCase();

  if (/ipad|tablet|playbook|silk/.test(userAgent)) {
    return "Tablet";
  }

  if (/mobi|android|iphone|ipod|phone/.test(userAgent)) {
    return "Phone";
  }

  return "Computer";
}

export function deviceIconPath(deviceType: string): string {
  if (deviceType === "Phone") {
    return "/phone.svg";
  }

  if (deviceType === "Tablet") {
    return "/tablet.svg";
  }

  return "/computer.svg";
}
