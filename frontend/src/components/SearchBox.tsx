type SearchBoxProps = {
  value: string;
  onChange: (value: string) => void;
  placeholder?: string;
};

/** Plain client-side text filter input, shared by the Sources/Targets/Subscriptions list pages. */
export function SearchBox({ value, onChange, placeholder = "Search..." }: SearchBoxProps) {
  return (
    <input
      type="search"
      className="search-box"
      value={value}
      onChange={(e) => onChange(e.target.value)}
      placeholder={placeholder}
      aria-label={placeholder}
    />
  );
}
