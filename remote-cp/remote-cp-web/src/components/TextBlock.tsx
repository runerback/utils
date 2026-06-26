import { useCallback, useEffect, useRef, useState } from "preact/hooks";

interface TextBlockProps {
  text: string;
}

const COLLAPSED_LINES = 8;

export function TextBlock({ text }: TextBlockProps) {
  const textRef = useRef<HTMLParagraphElement>(null);
  const [isExpanded, setIsExpanded] = useState(false);
  const [isOverflowing, setIsOverflowing] = useState(false);

  const checkOverflow = useCallback(() => {
    const el = textRef.current;
    if (!el) return;
    el.classList.add("message-text--collapsed");
    const overflowing = el.scrollHeight > el.clientHeight + 1;
    setIsOverflowing(overflowing);
    if (!overflowing) {
      el.classList.remove("message-text--collapsed");
    }
  }, []);

  useEffect(() => {
    checkOverflow();
    window.addEventListener("resize", checkOverflow);
    return () => window.removeEventListener("resize", checkOverflow);
  }, [checkOverflow]);

  return (
    <div class="message-text-block">
      <p
        ref={textRef}
        class={`message-text ${!isExpanded ? "message-text--collapsed" : ""}`}
        style={{ maxHeight: isOverflowing && !isExpanded ? `calc(1.5em * ${COLLAPSED_LINES})` : undefined }}
      >
        {text}
      </p>
      {isOverflowing && (
        <button
          type="button"
          class="secondary-button message-toggle"
          onClick={() => setIsExpanded(!isExpanded)}
        >
          {isExpanded ? "Show less" : "Show more"}
        </button>
      )}
    </div>
  );
}
