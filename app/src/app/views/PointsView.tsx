import type { Dataset, Settings } from "../../lib/types";
import useIsMobile from "../hooks/useIsMobile";
import PointsViewDesktop from "./PointsViewDesktop";
import PointsViewMobile from "./PointsViewMobile";

export default function PointsView(props: {
  dataset: Dataset;
  settings: Settings;
  onChange: (updater: (d: Dataset) => Dataset) => void;
}) {
  const isMobile = useIsMobile();
  return isMobile ? <PointsViewMobile {...props} /> : <PointsViewDesktop {...props} />;
}

