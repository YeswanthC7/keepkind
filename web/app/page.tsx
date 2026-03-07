"use client";

import { useEffect, useMemo, useRef, useState } from "react";

type DecisionOptionKey = "maintain" | "repair" | "resell" | "recycle";

type DecisionOption = {
  title: string;
  steps: string[];
  whatChangesThis: string[];
};

type DecisionArtifact = {
  itemId: number;
  summary: string;
  confidence: number;
  options: Record<DecisionOptionKey, DecisionOption>;
  citations: Array<{ chunkId: number; sourceId: number; distance: number }>;
  assumptions: string[];
  generation: {
    chatModel: string;
    embedModel: string;
    kUsed: number;
    promptVersion: string;
  };
};

type LocalReceipt = {
  id: string;
  createdAt: string;
  title: string;
  photo?: string;
  artifact: DecisionArtifact;
};

type ItemType = "phone" | "laptop" | "clothing" | "appliance" | "other" | "unknown";

type ZipMeta = {
  zip: string;
  city: string;
  state: string;
  country: string;
};

type ResourceLink = {
  label: string;
  href: string;
};

const API_BASE = process.env.NEXT_PUBLIC_API_BASE ?? "http://localhost:8080";
const STORAGE_KEY = "keepkind.receipts.v1";
const FIXED_DECISION_QUESTION = "What should I do with this item?";

function uuid(): string {
  return `${Date.now()}-${Math.random().toString(16).slice(2)}`;
}

function clamp(n: number, lo: number, hi: number): number {
  return Math.max(lo, Math.min(hi, n));
}

function formatPct(x: number): string {
  const p = clamp(x, 0, 1) * 100;
  return `${Math.round(p)}%`;
}

function safeJsonParse<T>(s: string | null): T | null {
  if (!s) return null;
  try {
    return JSON.parse(s) as T;
  } catch {
    return null;
  }
}

function isBlank(s: string | null | undefined): boolean {
  return !s || s.trim().length === 0;
}

function normalizeZip(zip: string): string {
  const digits = (zip ?? "").replace(/\D/g, "");
  return digits.slice(0, 5);
}

function isValidUsZip5(zip: string): boolean {
  return /^[0-9]{5}$/.test(zip);
}

function formatLocation(meta: ZipMeta | null): string | null {
  if (!meta) return null;
  const city = meta.city?.trim();
  const state = meta.state?.trim();
  const zip = meta.zip?.trim();
  if (!city || !state || !zip) return null;
  return `${city}, ${state} ${zip}`;
}

function buildUiTitle(params: {
  itemType: ItemType;
  brand: string;
  model: string;
  condition: string;
  zipMeta: ZipMeta | null;
}): string {
  const typeLabel =
    params.itemType === "unknown" || params.itemType === "other" ? "item" : params.itemType;
  const parts: string[] = [];

  const bm = [params.brand?.trim(), params.model?.trim()].filter(Boolean).join(" ");
  if (bm) parts.push(bm);
  else parts.push(typeLabel);

  if (params.condition?.trim()) parts.push(params.condition.trim());

  const loc = formatLocation(params.zipMeta);
  if (loc) parts.push(`(${loc})`);

  return parts.join(" ");
}

function detectItemTypeFromFile(file: File): ItemType {
  const text = `${file.name} ${file.type}`.toLowerCase();

  if (
    text.includes("iphone") ||
    text.includes("android") ||
    text.includes("phone") ||
    text.includes("mobile") ||
    text.includes("galaxy") ||
    text.includes("pixel")
  ) {
    return "phone";
  }

  if (
    text.includes("laptop") ||
    text.includes("macbook") ||
    text.includes("notebook") ||
    text.includes("thinkpad") ||
    text.includes("xps")
  ) {
    return "laptop";
  }

  if (
    text.includes("shirt") ||
    text.includes("jacket") ||
    text.includes("jeans") ||
    text.includes("dress") ||
    text.includes("clothing") ||
    text.includes("shoe") ||
    text.includes("sneaker")
  ) {
    return "clothing";
  }

  if (
    text.includes("appliance") ||
    text.includes("microwave") ||
    text.includes("blender") ||
    text.includes("vacuum") ||
    text.includes("fridge") ||
    text.includes("refrigerator") ||
    text.includes("washer") ||
    text.includes("washing machine") ||
    text.includes("dryer")
  ) {
    return "appliance";
  }

  return "unknown";
}

function formatItemTypeLabel(itemType: ItemType): string {
  switch (itemType) {
    case "phone":
      return "Phone";
    case "laptop":
      return "Laptop";
    case "clothing":
      return "Clothing";
    case "appliance":
      return "Appliance";
    case "other":
      return "Other";
    default:
      return "Couldn’t identify item from photo yet";
  }
}

function buildSearchQuery(params: {
  itemType: ItemType;
  brand: string;
  model: string;
  issue: string;
}): string {
  const parts = [
    params.brand?.trim(),
    params.model?.trim(),
    params.itemType !== "unknown" && params.itemType !== "other" ? params.itemType : "",
    params.issue?.trim(),
  ].filter(Boolean);
  return parts.join(" ").trim() || "used item";
}

function getOptionResourceLinks(params: {
  optionKey: DecisionOptionKey;
  itemType: ItemType;
  brand: string;
  model: string;
  issue: string;
  zipMeta: ZipMeta | null;
}): ResourceLink[] {
  const q = buildSearchQuery(params);
  const location = params.zipMeta ? ` ${params.zipMeta.city} ${params.zipMeta.state}` : "";

  if (params.optionKey === "repair") {
    return [
      {
        label: "iFixit",
        href: `https://www.ifixit.com/Search?query=${encodeURIComponent(q)}`,
      },
      {
        label: "Google repair search",
        href: `https://www.google.com/search?q=${encodeURIComponent(`${q} repair${location}`)}`,
      },
      {
        label: "Yelp repair",
        href: `https://www.yelp.com/search?find_desc=${encodeURIComponent(`${q} repair`)}&find_loc=${encodeURIComponent(
          location.trim() || "United States"
        )}`,
      },
    ];
  }

  if (params.optionKey === "resell") {
    return [
      {
        label: "eBay",
        href: `https://www.ebay.com/sch/i.html?_nkw=${encodeURIComponent(q)}`,
      },
      {
        label: "Craigslist",
        href: `https://www.google.com/search?q=${encodeURIComponent(`${q} site:craigslist.org`)}`,
      },
      {
        label: "Facebook Marketplace",
        href: `https://www.google.com/search?q=${encodeURIComponent(`${q} Facebook Marketplace`)}`,
      },
    ];
  }

  if (params.optionKey === "recycle") {
    return [
      {
        label: "Earth911",
        href: `https://search.earth911.com/?what=${encodeURIComponent(q)}&where=${encodeURIComponent(
          params.zipMeta?.zip || ""
        )}`,
      },
      {
        label: "Google recycling search",
        href: `https://www.google.com/search?q=${encodeURIComponent(`${q} recycle${location}`)}`,
      },
      {
        label: "Manufacturer trade-in",
        href: `https://www.google.com/search?q=${encodeURIComponent(`${q} trade in`)}`,
      },
    ];
  }

  return [
    {
      label: "Care guides",
      href: `https://www.google.com/search?q=${encodeURIComponent(`${q} care guide`)}`,
    },
    {
      label: "Maintenance tips",
      href: `https://www.google.com/search?q=${encodeURIComponent(`${q} maintenance tips`)}`,
    },
  ];
}

async function fileToBase64(file: File): Promise<string> {
  return new Promise((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(reader.result as string);
    reader.onerror = reject;
    reader.readAsDataURL(file);
  });
}

export default function Home() {
  const [receipts, setReceipts] = useState<LocalReceipt[]>([]);
  const [activeId, setActiveId] = useState<string | null>(null);
  const [activeTab, setActiveTab] = useState<DecisionOptionKey>("maintain");

  const [k, setK] = useState<number>(5);

  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);

  const [itemType, setItemType] = useState<ItemType>("unknown");
  const [brand, setBrand] = useState<string>("");
  const [model, setModel] = useState<string>("");
  const [purchaseYear, setPurchaseYear] = useState<string>("");
  const [condition, setCondition] = useState<string>("broken");
  const [issue, setIssue] = useState<string>("");
  const [zip, setZip] = useState<string>("");

  const [zipMeta, setZipMeta] = useState<ZipMeta | null>(null);
  const [zipStatus, setZipStatus] = useState<"idle" | "loading" | "ok" | "invalid" | "not_found" | "error">("idle");

  const [loading, setLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);

  const fileInputRef = useRef<HTMLInputElement | null>(null);

  useEffect(() => {
    const loaded = safeJsonParse<LocalReceipt[]>(localStorage.getItem(STORAGE_KEY));
    if (loaded && Array.isArray(loaded)) {
      setReceipts(loaded);
      setActiveId(loaded[0]?.id ?? null);
    }
  }, []);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(receipts));
  }, [receipts]);

  useEffect(() => {
    return () => {
      if (previewUrl && previewUrl.startsWith("blob:")) {
        URL.revokeObjectURL(previewUrl);
      }
    };
  }, [previewUrl]);

  useEffect(() => {
    const z = normalizeZip(zip);
    if (!z) {
      setZipMeta(null);
      setZipStatus("idle");
      return;
    }
    if (!isValidUsZip5(z)) {
      setZipMeta(null);
      setZipStatus("invalid");
      return;
    }

    const controller = new AbortController();
    setZipStatus("loading");

    (async () => {
      try {
        const resp = await fetch(`https://api.zippopotam.us/us/${z}`, { signal: controller.signal });
        if (!resp.ok) {
          if (resp.status === 404) {
            setZipMeta(null);
            setZipStatus("not_found");
            return;
          }
          setZipMeta(null);
          setZipStatus("error");
          return;
        }

        const data = (await resp.json()) as any;
        const place = Array.isArray(data?.places) ? data.places[0] : null;

        const city = place?.["place name"];
        const state = place?.["state abbreviation"] ?? place?.["state"];
        const country = data?.["country abbreviation"] ?? data?.["country"];

        if (typeof city === "string" && typeof state === "string") {
          setZipMeta({
            zip: z,
            city,
            state,
            country: typeof country === "string" ? country : "US",
          });
          setZipStatus("ok");
        } else {
          setZipMeta(null);
          setZipStatus("error");
        }
      } catch (e) {
        if ((e as any)?.name === "AbortError") return;
        setZipMeta(null);
        setZipStatus("error");
      }
    })();

    return () => controller.abort();
  }, [zip]);

  const active = useMemo(() => receipts.find((r) => r.id === activeId) ?? null, [receipts, activeId]);

  function resetForm() {
    if (previewUrl && previewUrl.startsWith("blob:")) {
      URL.revokeObjectURL(previewUrl);
    }

    setActiveId(null);
    setActiveTab("maintain");
    setK(5);

    setSelectedFile(null);
    setPreviewUrl(null);

    setItemType("unknown");
    setBrand("");
    setModel("");
    setPurchaseYear("");
    setCondition("broken");
    setIssue("");
    setZip("");

    setZipMeta(null);
    setZipStatus("idle");

    setError(null);

    if (fileInputRef.current) fileInputRef.current.value = "";
  }

  function newReceipt() {
    resetForm();
  }

  function onFileSelected(file: File) {
    if (previewUrl && previewUrl.startsWith("blob:")) {
      URL.revokeObjectURL(previewUrl);
    }

    setSelectedFile(file);
    setPreviewUrl(URL.createObjectURL(file));
    setItemType(detectItemTypeFromFile(file));
    setBrand("");
    setModel("");
    setError(null);
  }

  async function ensureItem(): Promise<number> {
    const resp = await fetch(`${API_BASE}/items`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ name: "item" }),
    });
    if (!resp.ok) {
      const text = await resp.text().catch(() => "");
      throw new Error(`Create item failed (${resp.status}): ${text || resp.statusText}`);
    }
    const json = (await resp.json()) as { id: number };
    if (!json?.id) throw new Error("Create item failed: missing id");
    return json.id;
  }

  async function attachTextSource(itemId: number, text: string): Promise<{ sourceId: number }> {
    const resp = await fetch(`${API_BASE}/items/${itemId}/sources/text`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        title: "User-provided details (MVP)",
        text,
        trust_level: "user",
      }),
    });
    if (!resp.ok) {
      const t = await resp.text().catch(() => "");
      throw new Error(`Attach source failed (${resp.status}): ${t || resp.statusText}`);
    }
    const json = (await resp.json()) as { sourceId?: number; id?: number };
    const sourceId = (json as any).sourceId ?? (json as any).id;
    if (!sourceId) throw new Error("Attach source failed: missing sourceId");
    return { sourceId: Number(sourceId) };
  }

  async function embedSource(sourceId: number): Promise<void> {
    const resp = await fetch(`${API_BASE}/sources/${sourceId}/embed`, { method: "POST" });
    if (!resp.ok) {
      const t = await resp.text().catch(() => "");
      throw new Error(`Embed source failed (${resp.status}): ${t || resp.statusText}`);
    }
  }

  async function runDecision(itemId: number): Promise<DecisionArtifact> {
    const url = `${API_BASE}/items/${itemId}/decision?q=${encodeURIComponent(
      FIXED_DECISION_QUESTION
    )}&k=${encodeURIComponent(String(clamp(k, 1, 10)))}`;
    const resp = await fetch(url, { method: "POST" });
    if (!resp.ok) {
      const text = await resp.text().catch(() => "");
      throw new Error(`Decision failed (${resp.status}): ${text || resp.statusText}`);
    }
    return (await resp.json()) as DecisionArtifact;
  }

  function buildSeedText(fileName: string): string {
    const lines: string[] = [];

    lines.push(`User uploaded an item photo (filename: ${fileName}).`);
    lines.push(`User question: ${FIXED_DECISION_QUESTION}`);
    lines.push("");
    lines.push("User-provided details:");

    if (itemType === "unknown") lines.push("- item_type: unknown_from_photo");
    else lines.push(`- item_type: ${itemType}`);

    if (!isBlank(brand)) lines.push(`- brand: ${brand.trim()}`);
    if (!isBlank(model)) lines.push(`- model: ${model.trim()}`);
    if (!isBlank(purchaseYear)) lines.push(`- purchase_year: ${purchaseYear.trim()}`);
    if (!isBlank(condition)) lines.push(`- condition: ${condition.trim()}`);
    if (!isBlank(issue)) lines.push(`- issue: ${issue.trim()}`);

    const z = normalizeZip(zip);
    if (isValidUsZip5(z)) {
      const loc = formatLocation(zipMeta);
      if (loc) {
        lines.push(`- location: ${loc}`);
        lines.push(`- zip: ${z}`);
      } else {
        lines.push(`- zip: ${z}`);
      }
    }

    lines.push("");
    lines.push("Output requirements:");
    lines.push("- Provide maintain, repair, resell, recycle options as separate receipts.");
    lines.push("- Be concise and practical.");
    lines.push("- If the item type is unknown, say so clearly instead of pretending it is known.");
    lines.push("- If more information would change the recommendation, mention it under 'what would change the recommendation'.");
    lines.push("- If repair is chosen, prefer nearby options if location is present; otherwise ask for zip.");
    lines.push("- Do NOT guess a state/city from zip; use provided location only.");

    return lines.join("\n");
  }

  async function generateReceipt() {
    if (!selectedFile) {
      setError("Please upload a photo first.");
      return;
    }

    setLoading(true);
    setError(null);

    try {
      const itemId = await ensureItem();

      const seed = buildSeedText(selectedFile.name);
      const { sourceId } = await attachTextSource(itemId, seed);
      await embedSource(sourceId);

      const artifact = await runDecision(itemId);

      const id = uuid();
      const createdAt = new Date().toISOString();
      const base64 = await fileToBase64(selectedFile);

      const title = buildUiTitle({
        itemType,
        brand,
        model,
        condition,
        zipMeta: zipStatus === "ok" ? zipMeta : null,
      });

      const rec: LocalReceipt = {
        id,
        createdAt,
        title,
        photo: base64,
        artifact,
      };

      setReceipts((prev) => [rec, ...prev]);
      setActiveId(id);
      setActiveTab("maintain");
    } catch (e: unknown) {
      const msg = e instanceof Error ? e.message : "Unknown error";
      setError(msg);
    } finally {
      setLoading(false);
    }
  }

  function deleteReceipt(id: string) {
    setReceipts((prev) => prev.filter((r) => r.id !== id));
    if (activeId === id) {
      const next = receipts.find((r) => r.id !== id)?.id ?? null;
      setActiveId(next);
    }
  }

  const showBrandModel =
    selectedFile !== null && (itemType === "phone" || itemType === "laptop" || itemType === "appliance");

  const activeResourceLinks = active
    ? getOptionResourceLinks({
        optionKey: activeTab,
        itemType,
        brand,
        model,
        issue,
        zipMeta: zipStatus === "ok" ? zipMeta : null,
      })
    : [];

  return (
    <div className="min-h-screen bg-white text-zinc-950">
      <div className="flex min-h-screen">
        <aside className="w-[320px] border-r border-zinc-200 bg-white">
          <div className="flex items-center justify-between px-4 py-4">
            <div>
              <div className="text-sm font-semibold tracking-tight">KeepKind</div>
              <div className="text-xs text-zinc-500">Decision receipts</div>
            </div>
            <button
              onClick={newReceipt}
              className="rounded-md border border-zinc-200 px-3 py-1.5 text-xs font-medium hover:bg-zinc-50"
            >
              New
            </button>
          </div>

          <div className="px-2 pb-4">
            {receipts.length === 0 ? (
              <div className="px-2 py-4 text-sm text-zinc-500">No receipts yet.</div>
            ) : (
              <ul className="space-y-1">
                {receipts.map((r) => {
                  const isActive = r.id === activeId;
                  return (
                    <li key={r.id}>
                      <button
                        onClick={() => {
                          setActiveId(r.id);
                          setActiveTab("maintain");
                          setError(null);
                        }}
                        className={[
                          "w-full rounded-md px-3 py-2 text-left",
                          isActive ? "bg-zinc-100" : "hover:bg-zinc-50",
                        ].join(" ")}
                      >
                        <div className="flex items-start justify-between gap-2">
                          <div className="min-w-0">
                            <div className="truncate text-sm font-medium">{r.title}</div>
                            <div className="text-xs text-zinc-500">{new Date(r.createdAt).toLocaleString()}</div>
                          </div>
                          <span className="text-[10px] text-zinc-400">#{r.artifact.itemId}</span>
                        </div>
                      </button>
                      <div className="flex justify-end px-3 pb-1">
                        <button
                          onClick={() => deleteReceipt(r.id)}
                          className="text-[11px] text-zinc-500 hover:text-zinc-900"
                        >
                          Remove
                        </button>
                      </div>
                    </li>
                  );
                })}
              </ul>
            )}
          </div>
        </aside>

        <main className="flex-1">
          <div className="mx-auto max-w-3xl px-6 py-8">
            <div className="mb-6">
              <div className="text-xl font-semibold tracking-tight">Decision receipt</div>
              <div className="mt-1 text-sm text-zinc-500">
                Upload an item to generate maintain / repair / resell / recycle.
              </div>
            </div>

            {!active && (
              <div className="rounded-xl border border-zinc-200 bg-white p-6">
                <div className="text-sm font-medium">Get started</div>
                <div className="mt-1 text-sm text-zinc-600">
                  Upload a photo first. Then review a few details before generating the receipt.
                </div>

                <div className="mt-4 grid gap-4">
                  <div className="flex items-center gap-3">
                    <input
                      ref={fileInputRef}
                      type="file"
                      accept="image/*"
                      className="hidden"
                      onChange={(e) => {
                        const f = e.target.files?.[0];
                        if (f) onFileSelected(f);
                      }}
                    />
                    <button
                      disabled={loading}
                      onClick={() => fileInputRef.current?.click()}
                      className="rounded-md bg-zinc-950 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-800 disabled:opacity-50"
                    >
                      {selectedFile ? "Replace photo" : "Upload photo"}
                    </button>
                    <div className="text-xs text-zinc-500">
                      {selectedFile ? `Selected: ${selectedFile.name}` : "No photo selected yet."}
                    </div>
                  </div>

                  {selectedFile && (
                    <>
                      {previewUrl && (
                        <div className="grid gap-2">
                          <label className="text-xs font-medium text-zinc-700">Uploaded photo</label>
                          <img
                            src={previewUrl}
                            alt="Uploaded item"
                            className="max-h-[280px] w-full max-w-[360px] rounded-lg border border-zinc-200 object-contain"
                          />
                        </div>
                      )}

                      <div className="grid gap-2">
                        <label className="text-xs font-medium text-zinc-700">Detected item type</label>
                        <div className="w-full max-w-[360px] rounded-md border border-zinc-200 bg-zinc-50 px-3 py-2 text-sm text-zinc-900">
                          {formatItemTypeLabel(itemType)}
                        </div>
                        <div className="text-xs text-zinc-500">
                          {itemType === "unknown"
                            ? "Current MVP cannot reliably read the image itself yet."
                            : "Detected from the uploaded photo context."}
                        </div>
                      </div>

                      {showBrandModel && (
                        <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                          <div className="grid gap-2">
                            <label className="text-xs font-medium text-zinc-700">Brand</label>
                            <input
                              value={brand}
                              onChange={(e) => setBrand(e.target.value)}
                              className="w-full rounded-md border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-400"
                              placeholder="Apple / Samsung / Dell / LG"
                            />
                          </div>
                          <div className="grid gap-2">
                            <label className="text-xs font-medium text-zinc-700">Model</label>
                            <input
                              value={model}
                              onChange={(e) => setModel(e.target.value)}
                              className="w-full rounded-md border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-400"
                              placeholder="iPhone 13 / Galaxy S22 / XPS 13"
                            />
                          </div>
                        </div>
                      )}

                      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                        <div className="grid gap-2">
                          <label className="text-xs font-medium text-zinc-700">Purchase year (optional)</label>
                          <input
                            value={purchaseYear}
                            onChange={(e) => setPurchaseYear(e.target.value)}
                            className="w-full rounded-md border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-400"
                            placeholder="2022"
                          />
                        </div>
                        <div className="grid gap-2">
                          <label className="text-xs font-medium text-zinc-700">Zip code (optional, for repair)</label>
                          <input
                            value={zip}
                            onChange={(e) => setZip(e.target.value)}
                            className="w-full rounded-md border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-400"
                            placeholder="75080"
                            inputMode="numeric"
                          />
                          <div className="text-xs text-zinc-500">
                            {zipStatus === "idle" && <span>&nbsp;</span>}
                            {zipStatus === "loading" && <span>Looking up location…</span>}
                            {zipStatus === "invalid" && <span>Enter a 5-digit US ZIP.</span>}
                            {zipStatus === "not_found" && <span>ZIP not found.</span>}
                            {zipStatus === "error" && <span>Couldn’t verify ZIP right now.</span>}
                            {zipStatus === "ok" && zipMeta && <span>{zipMeta.city}, {zipMeta.state}</span>}
                          </div>
                        </div>
                      </div>

                      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2">
                        <div className="grid gap-2">
                          <label className="text-xs font-medium text-zinc-700">Condition</label>
                          <select
                            value={condition}
                            onChange={(e) => setCondition(e.target.value)}
                            className="w-full rounded-md border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-400"
                          >
                            <option value="like_new">Like new</option>
                            <option value="good">Good</option>
                            <option value="worn">Worn</option>
                            <option value="broken">Broken</option>
                          </select>
                        </div>
                        <div className="grid gap-2">
                          <label className="text-xs font-medium text-zinc-700">Issue / symptoms (optional)</label>
                          <input
                            value={issue}
                            onChange={(e) => setIssue(e.target.value)}
                            className="w-full rounded-md border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-400"
                            placeholder="Cracked screen / won’t charge / torn seam"
                          />
                        </div>
                      </div>

                      <div className="grid gap-2">
                        <label className="text-xs font-medium text-zinc-700">Top-k context</label>
                        <input
                          type="number"
                          min={1}
                          max={10}
                          value={k}
                          onChange={(e) => setK(Number(e.target.value))}
                          className="w-32 rounded-md border border-zinc-200 px-3 py-2 text-sm outline-none focus:border-zinc-400"
                        />
                      </div>

                      <div className="flex items-center gap-3">
                        <button
                          disabled={loading}
                          onClick={generateReceipt}
                          className="rounded-md bg-zinc-950 px-4 py-2 text-sm font-medium text-white hover:bg-zinc-800 disabled:opacity-50"
                        >
                          {loading ? "Generating…" : "Generate receipt"}
                        </button>
                        <div className="text-xs text-zinc-500">MVP: photo upload is UI-first; text details still seed retrieval.</div>
                      </div>
                    </>
                  )}

                  {error && (
                    <div className="rounded-md border border-red-200 bg-red-50 px-3 py-2 text-sm text-red-800">
                      {error}
                    </div>
                  )}
                </div>
              </div>
            )}

            {active && (
              <div className="rounded-xl border border-zinc-200 bg-white">
                <div className="border-b border-zinc-200 px-6 py-4">
                  <div className="flex items-start justify-between gap-4">
                    <div className="min-w-0">
                      <div className="truncate text-base font-semibold">{active.title}</div>

                      <div className="mt-1 text-xs text-zinc-500">
                        Confidence: {formatPct(active.artifact.confidence)} · Model: {active.artifact.generation.chatModel}
                      </div>
                    </div>
                    <button
                      onClick={newReceipt}
                      className="rounded-md border border-zinc-200 px-3 py-1.5 text-xs font-medium hover:bg-zinc-50"
                    >
                      New
                    </button>
                  </div>

                  <div className="mt-4 inline-flex rounded-lg border border-zinc-200 p-1">
                    {(["maintain", "repair", "resell", "recycle"] as DecisionOptionKey[]).map((key) => {
                      const activeKey = key === activeTab;
                      return (
                        <button
                          key={key}
                          onClick={() => setActiveTab(key)}
                          className={[
                            "rounded-md px-3 py-1.5 text-xs font-medium capitalize",
                            activeKey ? "bg-zinc-950 text-white" : "text-zinc-700 hover:bg-zinc-50",
                          ].join(" ")}
                        >
                          {key}
                        </button>
                      );
                    })}
                  </div>
                </div>

                <div className="px-6 py-5">
                  {active.photo && (
                    <div className="mb-6">
                      <img
                        src={active.photo}
                        alt="Uploaded item"
                        className="max-h-[300px] rounded-lg border border-zinc-200 object-contain"
                      />
                    </div>
                  )}

                  <OptionCard option={active.artifact.options[activeTab]} />

                  <div className="mt-4">
                    <div className="mb-2 text-xs font-medium text-zinc-700">Helpful resources</div>
                    <div className="flex flex-wrap gap-2">
                      {activeResourceLinks.map((link) => (
                        <a
                          key={link.label}
                          href={link.href}
                          target="_blank"
                          rel="noreferrer"
                          className="rounded-full border border-zinc-200 px-3 py-1.5 text-xs font-medium text-zinc-700 hover:bg-zinc-50"
                        >
                          {link.label}
                        </a>
                      ))}
                    </div>
                  </div>
                </div>
              </div>
            )}
          </div>
        </main>
      </div>
    </div>
  );
}

function OptionCard({ option }: { option: DecisionOption }) {
  return (
    <div className="rounded-lg border border-zinc-200 p-4">
      <div className="text-sm font-semibold">{option.title}</div>

      <div className="mt-3">
        <div className="text-xs font-medium text-zinc-700">Steps</div>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-zinc-800">
          {(option.steps ?? []).map((s, idx) => (
            <li key={idx}>{s}</li>
          ))}
        </ul>
      </div>

      <div className="mt-4">
        <div className="text-xs font-medium text-zinc-700">What would change the recommendation</div>
        <ul className="mt-2 list-disc space-y-1 pl-5 text-sm text-zinc-800">
          {(option.whatChangesThis ?? []).map((s, idx) => (
            <li key={idx}>{s}</li>
          ))}
        </ul>
      </div>
    </div>
  );
}