import type { NextConfig } from "next";

const nextConfig: NextConfig = {
  // Standalone output = a minimal, self-contained server bundle for the Docker image (see Dockerfile) —
  // copies only the traced production dependencies instead of the full node_modules tree.
  output: "standalone",
};

export default nextConfig;
