import type { Dataset, Id } from "../../lib/types";
import useIsMobile from "../hooks/useIsMobile";
import SpeciesViewDesktop from "./SpeciesViewDesktop";
import SpeciesViewMobile from "./SpeciesViewMobile";

export default function SpeciesView(props: {
  dataset: Dataset;
  onChange: (updater: (d: Dataset) => Dataset) => void;
  onOpenWetWeight: (speciesId: Id) => void;
  onOpenTaxonomy: (speciesId: Id) => void;
}) {
  const isMobile = useIsMobile();
  return isMobile ? <SpeciesViewMobile {...props} /> : <SpeciesViewDesktop {...props} />;
}

