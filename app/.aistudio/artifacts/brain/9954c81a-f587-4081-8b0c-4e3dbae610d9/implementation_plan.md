# Implementation Plan - Hierarchical Tree & Collapsible Control Panel Network Topology

## Objective
Enhance the Network Nodes topology view by adding a Hierarchical Tree / Branching network visualization mode alongside the spiderweb view, featuring a collapsible dropdown control bar and optimized initial scaling for small mobile screens.

## Proposed Changes
1. **Collapsible Control Bar (شريط تحكم قابل للطي)**:
   - Add a compact toggle header in `SpiderwebTopologyGraph` (and new Hierarchical Tree view) to collapse or expand zoom, spacing, and view mode controls cleanly.

2. **Hierarchical Tree / Branching Topology (نمط الشجرة الهرمية المتسلسلة)**:
   - Implement a cyber-themed branching tree view where the Main Router sits at the top as the root, branching down into distribution nodes (APs / Towers) and sub-clients.
   - Support smooth pan, zoom, and interactive node selection.

3. **Responsive Auto-Fit for Small Screens**:
   - Automatically adjust initial zoom scale and node spacing when loaded on compact viewports or dense node sets.

## Verification Plan
- Run `compile_applet` to verify compilation and successful build.
