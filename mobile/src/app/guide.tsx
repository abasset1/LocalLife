import { useRouter } from 'expo-router';
import { useCallback, useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  AccessibilityInfo,
  Animated,
  Easing,
  Pressable,
  ScrollView,
  StyleSheet,
  View,
  type StyleProp,
  type ViewStyle,
} from 'react-native';
import { SafeAreaView } from 'react-native-safe-area-context';

import { Button } from '@/components/button';
import { ThemedText } from '@/components/themed-text';
import { ThemedView } from '@/components/themed-view';
import { Brand, Radius, Spacing } from '@/constants/theme';
import {
  guideStepsFor,
  GUIDE_STEP_DURATION_MS,
  clampStep,
  nextAutoplayStep,
  type GuideRow,
  type GuideScene,
} from '@/features/guide/steps';
import { useLaunchFeatures } from '@/features/launch';
import { useTheme } from '@/hooks/use-theme';

/** Préférence système « Réduire les animations » : scènes fixes et pas de lecture automatique. */
function useReduceMotion(): boolean | null {
  const [reduce, setReduce] = useState<boolean | null>(null);
  useEffect(() => {
    let mounted = true;
    AccessibilityInfo.isReduceMotionEnabled()
      .then((value) => mounted && setReduce(value))
      .catch(() => mounted && setReduce(false));
    const subscription = AccessibilityInfo.addEventListener('reduceMotionChanged', setReduce);
    return () => {
      mounted = false;
      subscription.remove();
    };
  }, []);
  return reduce;
}

/** Apparition (fondu + glissement ou rebond) après `delay` ms. */
function Appear({
  delay,
  reduce,
  from = 'bottom',
  style,
  children,
}: {
  delay: number;
  reduce: boolean;
  from?: 'bottom' | 'top' | 'left' | 'pop';
  style?: StyleProp<ViewStyle>;
  children: ReactNode;
}) {
  const [value] = useState(() => new Animated.Value(reduce ? 1 : 0));
  useEffect(() => {
    if (reduce) {
      value.setValue(1);
      return;
    }
    const animation =
      from === 'pop' || from === 'top'
        ? Animated.spring(value, { toValue: 1, delay, friction: 5, tension: 80, useNativeDriver: true })
        : Animated.timing(value, {
            toValue: 1,
            delay,
            duration: 420,
            easing: Easing.out(Easing.cubic),
            useNativeDriver: true,
          });
    animation.start();
    return () => animation.stop();
  }, [delay, from, reduce, value]);

  const transform =
    from === 'pop'
      ? [{ scale: value.interpolate({ inputRange: [0, 1], outputRange: [0.3, 1] }) }]
      : from === 'top'
        ? [{ translateY: value.interpolate({ inputRange: [0, 1], outputRange: [-90, 0] }) }]
        : from === 'left'
          ? [{ translateX: value.interpolate({ inputRange: [0, 1], outputRange: [-28, 0] }) }]
          : [{ translateY: value.interpolate({ inputRange: [0, 1], outputRange: [16, 0] }) }];

  return <Animated.View style={[style, { opacity: value, transform }]}>{children}</Animated.View>;
}

/** Mouvement continu de flottement (ou pulsation). */
function Float({ reduce, children, style }: { reduce: boolean; children: ReactNode; style?: StyleProp<ViewStyle> }) {
  const [value] = useState(() => new Animated.Value(0));
  useEffect(() => {
    if (reduce) return;
    const loop = Animated.loop(
      Animated.sequence([
        Animated.timing(value, { toValue: 1, duration: 1400, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
        Animated.timing(value, { toValue: 0, duration: 1400, easing: Easing.inOut(Easing.sin), useNativeDriver: true }),
      ]),
    );
    loop.start();
    return () => loop.stop();
  }, [reduce, value]);
  const translateY = value.interpolate({ inputRange: [0, 1], outputRange: [0, -10] });
  return <Animated.View style={[style, { transform: [{ translateY }] }]}>{children}</Animated.View>;
}

/** Vrai après `delay` ms (tout de suite si les animations sont réduites). */
function useAfter(delay: number, reduce: boolean): boolean {
  // Sans animation, l'état final est l'état initial (la scène est recréée si la préférence change).
  const [done, setDone] = useState(reduce);
  useEffect(() => {
    if (reduce) return;
    const timer = setTimeout(() => setDone(true), delay);
    return () => clearTimeout(timer);
  }, [delay, reduce]);
  return done;
}

function Row({ row }: { row: GuideRow }) {
  const theme = useTheme();
  return (
    <View style={[styles.row, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
      <View style={[styles.thumb, { backgroundColor: theme.sand }]}>
        <ThemedText style={styles.thumbEmoji}>{row.emoji}</ThemedText>
      </View>
      <View style={styles.rowText}>
        <ThemedText type="smallBold" numberOfLines={1}>
          {row.title}
        </ThemedText>
        <ThemedText type="small" themeColor="textSecondary" numberOfLines={1}>
          {row.meta}
        </ThemedText>
      </View>
    </View>
  );
}

const PIN_POSITIONS = [
  { left: '22%', top: '28%' },
  { left: '58%', top: '50%' },
  { left: '72%', top: '22%' },
  { left: '38%', top: '62%' },
] as const;

const ORBIT_POSITIONS = [
  { left: '44%', top: '4%' },
  { left: '78%', top: '24%' },
  { left: '78%', top: '64%' },
  { left: '44%', top: '82%' },
  { left: '10%', top: '64%' },
  { left: '10%', top: '24%' },
] as const;

function HeroScene({ emoji, orbit, reduce }: { emoji: string; orbit: string[]; reduce: boolean }) {
  const theme = useTheme();
  return (
    <View style={styles.sceneFill}>
      {orbit.map((item, index) => (
        <Appear
          delay={300 + index * 180}
          from="pop"
          key={`${item}-${index}`}
          reduce={reduce}
          style={[styles.orbitItem, ORBIT_POSITIONS[index % ORBIT_POSITIONS.length], { backgroundColor: theme.sand }]}>
          <ThemedText style={styles.orbitEmoji}>{item}</ThemedText>
        </Appear>
      ))}
      <View style={styles.centered}>
        <Appear delay={0} from="pop" reduce={reduce}>
          <Float reduce={reduce} style={styles.heroBadge}>
            <ThemedText style={styles.heroEmoji}>{emoji}</ThemedText>
          </Float>
        </Appear>
      </View>
    </View>
  );
}

function MapScene({
  layers,
  preview,
  reduce,
}: {
  layers: { label: string; pins: string[] }[];
  preview?: GuideRow;
  reduce: boolean;
}) {
  const [layerIndex, setLayerIndex] = useState(0);
  const showPreview = useAfter(2600, reduce);
  useEffect(() => {
    if (reduce || layers.length < 2) return;
    const timer = setInterval(() => setLayerIndex((current) => (current + 1) % layers.length), 2800);
    return () => clearInterval(timer);
  }, [layers.length, reduce]);
  const layer = layers[layerIndex];

  return (
    <View style={[styles.sceneFill, styles.map]}>
      <View style={[styles.road, styles.road1]} />
      <View style={[styles.road, styles.road2]} />
      <View style={styles.water} />
      {layers.length > 1 && (
        <View style={styles.layerSwitch}>
          {layers.map((item, index) => (
            <View key={item.label} style={[styles.layerButton, index === layerIndex && styles.layerButtonActive]}>
              <ThemedText type="small" style={[styles.layerLabel, index === layerIndex && styles.layerLabelActive]}>
                {item.label}
              </ThemedText>
            </View>
          ))}
        </View>
      )}
      {/* `key` : à chaque couche, les marqueurs retombent. */}
      <View key={layerIndex} style={StyleSheet.absoluteFill}>
        {layer.pins.map((pin, index) => (
          <Appear
            delay={250 + index * 200}
            from="top"
            key={index}
            reduce={reduce}
            style={[styles.pin, PIN_POSITIONS[index % PIN_POSITIONS.length]]}>
            <ThemedText style={styles.pinEmoji}>{pin}</ThemedText>
          </Appear>
        ))}
      </View>
      {preview && showPreview && (
        <Appear delay={0} reduce={reduce} style={styles.preview}>
          <Row row={preview} />
        </Appear>
      )}
    </View>
  );
}

function SearchScene({ query, rows, reduce }: { query: string; rows: GuideRow[]; reduce: boolean }) {
  const theme = useTheme();
  const [length, setLength] = useState(reduce ? query.length : 0);
  useEffect(() => {
    if (reduce) return;
    let interval: ReturnType<typeof setInterval> | undefined;
    const start = setTimeout(() => {
      interval = setInterval(() => {
        setLength((current) => {
          if (current >= query.length && interval) clearInterval(interval);
          return Math.min(query.length, current + 1);
        });
      }, 90);
    }, 500);
    return () => {
      clearTimeout(start);
      if (interval) clearInterval(interval);
    };
  }, [query, reduce]);
  const typed = length >= query.length;
  const rowsDelay = reduce ? 0 : 500 + query.length * 90 + 300;

  return (
    <View style={styles.sceneColumn}>
      <View style={[styles.searchBar, { backgroundColor: theme.backgroundElement, borderColor: theme.primary }]}>
        <ThemedText>🔍</ThemedText>
        <ThemedText numberOfLines={1} style={styles.searchText}>
          {query.slice(0, length)}
          {!typed && <ThemedText style={{ color: theme.accent }}>|</ThemedText>}
        </ThemedText>
      </View>
      {rows.map((row, index) => (
        <Appear delay={rowsDelay + index * 220} key={row.title} reduce={reduce}>
          <Row row={row} />
        </Appear>
      ))}
    </View>
  );
}

function Chip({ label, selected, delay, reduce }: { label: string; selected: boolean; delay: number; reduce: boolean }) {
  const theme = useTheme();
  const on = useAfter(delay, reduce) && selected;
  return (
    <View
      style={[
        styles.chip,
        { borderColor: theme.border, backgroundColor: theme.backgroundElement },
        on && { backgroundColor: theme.primary, borderColor: theme.primary },
      ]}>
      <ThemedText type="small" style={on ? { color: theme.onPrimary } : undefined}>
        {label}
      </ThemedText>
    </View>
  );
}

function ChipsScene({
  groups,
  reduce,
}: {
  groups: { label: string; chips: string[]; selected: number[] }[];
  reduce: boolean;
}) {
  let order = 0;
  return (
    <View style={styles.sceneColumn}>
      {groups.map((group, groupIndex) => (
        <Appear delay={groupIndex * 250} key={group.label} reduce={reduce} style={styles.chipGroup}>
          <ThemedText type="eyebrow">{group.label}</ThemedText>
          <View style={styles.chipRow}>
            {group.chips.map((chip, index) => {
              const selected = group.selected.includes(index);
              const delay = selected ? 900 + order++ * 650 : 0;
              return <Chip delay={delay} key={chip} label={chip} reduce={reduce} selected={selected} />;
            })}
          </View>
        </Appear>
      ))}
    </View>
  );
}

function RowsScene({ header, rows, reduce }: { header?: string; rows: GuideRow[]; reduce: boolean }) {
  return (
    <View style={styles.sceneColumn}>
      {header && (
        <Appear delay={0} from="pop" reduce={reduce}>
          <ThemedText type="smallBold" style={styles.rowsHeader}>
            {header}
          </ThemedText>
        </Appear>
      )}
      {rows.map((row, index) => (
        <Appear delay={350 + index * 380} from="left" key={row.title} reduce={reduce}>
          <Row row={row} />
        </Appear>
      ))}
    </View>
  );
}

function FlowScene({ stages, reduce }: { stages: string[]; reduce: boolean }) {
  const theme = useTheme();
  return (
    <View style={[styles.sceneColumn, styles.flow]}>
      {stages.map((stage, index) => (
        <View key={stage} style={styles.flowItem}>
          {index > 0 && (
            <Appear delay={index * 1300 - 400} reduce={reduce}>
              <ThemedText style={[styles.flowArrow, { color: theme.textSecondary }]}>↓</ThemedText>
            </Appear>
          )}
          <Appear delay={index * 1300} from="pop" reduce={reduce}>
            <View
              style={[
                styles.flowStage,
                {
                  backgroundColor:
                    index === stages.length - 1 ? theme.successSoft : index === 0 ? theme.primarySoft : theme.warningSoft,
                },
              ]}>
              <ThemedText type="smallBold">{stage}</ThemedText>
            </View>
          </Appear>
        </View>
      ))}
    </View>
  );
}

function Scene({ scene, reduce }: { scene: GuideScene; reduce: boolean }) {
  switch (scene.kind) {
    case 'hero':
      return <HeroScene emoji={scene.emoji} orbit={scene.orbit} reduce={reduce} />;
    case 'map':
      return <MapScene layers={scene.layers} preview={scene.preview} reduce={reduce} />;
    case 'search':
      return <SearchScene query={scene.query} reduce={reduce} rows={scene.rows} />;
    case 'chips':
      return <ChipsScene groups={scene.groups} reduce={reduce} />;
    case 'rows':
      return <RowsScene header={scene.header} reduce={reduce} rows={scene.rows} />;
    case 'flow':
      return <FlowScene reduce={reduce} stages={scene.stages} />;
  }
}

/**
 * Guide de l'application (`/guide`) : présentation animée, pas à pas, des possibilités de LocalLife
 * (même parcours que le guide du site web). Lecture automatique avec barre de progression, pause,
 * étapes précédente / suivante ; avec « Réduire les animations », les scènes sont fixes et la lecture
 * automatique est coupée. Ouvert depuis le menu (« Aide »), Compte, les Paramètres de recherche et la
 * fin de l'accueil de première connexion.
 */
export default function GuideScreen() {
  const router = useRouter();
  const theme = useTheme();
  const reduceSetting = useReduceMotion();
  const reduce = reduceSetting === true;
  const [index, setIndex] = useState(0);
  const [playing, setPlaying] = useState(true);
  const [progress] = useState(() => new Animated.Value(0));
  // Étapes des fonctions ouvertes seulement (lancement progressif, 05/11/2026).
  const features = useLaunchFeatures();
  const steps = useMemo(() => guideStepsFor(features), [features]);
  const step = steps[Math.min(index, steps.length - 1)];
  const isLast = index >= steps.length - 1;
  const running = reduceSetting === false && playing && !isLast;

  const goTo = useCallback(
    (next: number) => {
      progress.stopAnimation();
      progress.setValue(0);
      setIndex(clampStep(next, steps));
    },
    [progress, steps],
  );

  // Lecture automatique : la barre de l'étape se remplit, puis l'étape suivante s'affiche. En pause, la barre
  // s'arrête ; la reprise repart de là où elle en était.
  useEffect(() => {
    if (!running) {
      progress.stopAnimation();
      return;
    }
    let remaining = GUIDE_STEP_DURATION_MS;
    progress.stopAnimation((value) => {
      remaining = Math.max(0, (1 - value) * GUIDE_STEP_DURATION_MS);
    });
    const animation = Animated.timing(progress, {
      toValue: 1,
      duration: remaining,
      easing: Easing.linear,
      useNativeDriver: false,
    });
    animation.start(({ finished }) => {
      if (!finished) return;
      const next = nextAutoplayStep(index, steps);
      if (next !== null) goTo(next);
    });
    return () => animation.stop();
  }, [running, index, progress, goTo, steps]);

  const close = useCallback(() => {
    if (router.canGoBack()) router.back();
    else router.replace('/');
  }, [router]);

  const currentWidth = progress.interpolate({ inputRange: [0, 1], outputRange: ['0%', '100%'] });

  return (
    <ThemedView style={styles.container}>
      <SafeAreaView edges={['bottom']} style={styles.container}>
        <View accessibilityElementsHidden importantForAccessibility="no-hide-descendants" style={styles.progress}>
          {steps.map((item, itemIndex) => (
            <View key={item.id} style={[styles.progressSegment, { backgroundColor: theme.border }]}>
              <Animated.View
                style={[
                  styles.progressFill,
                  {
                    width: itemIndex < index || (itemIndex === index && isLast) ? '100%' : itemIndex === index ? currentWidth : '0%',
                  },
                ]}
              />
            </View>
          ))}
        </View>

        <ScrollView contentContainerStyle={styles.content}>
          {/* `key` : changer d'étape rejoue les animations de la scène. */}
          <View
            key={`${step.id}-${reduceSetting === null ? 'pending' : String(reduce)}`}
            style={[styles.stage, { backgroundColor: theme.backgroundElement, borderColor: theme.border }]}>
            {reduceSetting !== null && <Scene reduce={reduce} scene={step.scene} />}
          </View>

          <View style={styles.copy}>
            <ThemedText type="eyebrow">
              Étape {index + 1} sur {steps.length}
            </ThemedText>
            <ThemedText type="subtitle" accessibilityRole="header">
              {step.title}
            </ThemedText>
            <ThemedText themeColor="textSecondary">{step.text}</ThemedText>
            {step.tips.map((tip) => (
              <View key={tip} style={[styles.tip, { backgroundColor: theme.accentSoft }]}>
                <ThemedText type="small">💡 {tip}</ThemedText>
              </View>
            ))}
            {isLast && (
              <View style={styles.cta}>
                <Button label="Explorer la carte" onPress={() => router.replace('/')} />
                <Button label="Revoir le guide" variant="secondary" onPress={() => goTo(0)} />
              </View>
            )}
          </View>
        </ScrollView>

        <View style={[styles.controls, { borderTopColor: theme.border }]}>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Étape précédente"
            accessibilityState={{ disabled: index === 0 }}
            disabled={index === 0}
            onPress={() => goTo(index - 1)}
            style={[styles.navButton, { borderColor: theme.border }, index === 0 && styles.disabled]}>
            <ThemedText type="smallBold" themeColor="primary">
              ←
            </ThemedText>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel={playing ? 'Mettre en pause' : 'Lecture automatique'}
            accessibilityState={{ disabled: isLast || reduce }}
            disabled={isLast || reduce}
            onPress={() => setPlaying((value) => !value)}
            style={[styles.navButton, { borderColor: theme.border }, (isLast || reduce) && styles.disabled]}>
            <ThemedText type="smallBold" themeColor="primary">
              {playing && !reduce ? '❚❚' : '▶'}
            </ThemedText>
          </Pressable>
          <Pressable accessibilityRole="button" onPress={close} style={styles.skip}>
            <ThemedText type="small" themeColor="textSecondary" style={styles.skipLabel}>
              {isLast ? 'Fermer' : 'Passer'}
            </ThemedText>
          </Pressable>
          <Pressable
            accessibilityRole="button"
            accessibilityLabel="Étape suivante"
            accessibilityState={{ disabled: isLast }}
            disabled={isLast}
            onPress={() => goTo(index + 1)}
            style={[styles.navButton, styles.nextButton, { backgroundColor: theme.primary }, isLast && styles.disabled]}>
            <ThemedText type="smallBold" style={{ color: theme.onPrimary }}>
              Suivant →
            </ThemedText>
          </Pressable>
        </View>
      </SafeAreaView>
    </ThemedView>
  );
}

const styles = StyleSheet.create({
  container: {
    flex: 1,
  },
  progress: {
    flexDirection: 'row',
    gap: 4,
    paddingHorizontal: Spacing.four,
    paddingTop: Spacing.two,
  },
  progressSegment: {
    flex: 1,
    height: 4,
    borderRadius: 2,
    overflow: 'hidden',
  },
  progressFill: {
    height: '100%',
    backgroundColor: Brand.coral,
  },
  content: {
    gap: Spacing.four,
    padding: Spacing.four,
    paddingBottom: Spacing.six,
  },
  stage: {
    height: 300,
    borderRadius: Radius.xl,
    borderWidth: StyleSheet.hairlineWidth,
    overflow: 'hidden',
  },
  copy: {
    gap: Spacing.two,
  },
  tip: {
    borderRadius: Radius.md,
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
  },
  cta: {
    gap: Spacing.two,
    marginTop: Spacing.two,
  },
  controls: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.four,
    paddingVertical: Spacing.two,
    borderTopWidth: StyleSheet.hairlineWidth,
  },
  navButton: {
    minWidth: 48,
    minHeight: 48,
    paddingHorizontal: Spacing.three,
    borderRadius: 24,
    borderWidth: 1,
    alignItems: 'center',
    justifyContent: 'center',
  },
  nextButton: {
    borderWidth: 0,
  },
  disabled: {
    opacity: 0.4,
  },
  skip: {
    flex: 1,
    minHeight: 48,
    alignItems: 'center',
    justifyContent: 'center',
  },
  skipLabel: {
    textDecorationLine: 'underline',
  },
  // --- Scènes
  sceneFill: {
    ...StyleSheet.absoluteFill,
  },
  sceneColumn: {
    flex: 1,
    justifyContent: 'center',
    gap: Spacing.two,
    padding: Spacing.three,
  },
  centered: {
    ...StyleSheet.absoluteFill,
    alignItems: 'center',
    justifyContent: 'center',
  },
  heroBadge: {
    width: 96,
    height: 96,
    borderRadius: 28,
    alignItems: 'center',
    justifyContent: 'center',
    backgroundColor: Brand.green,
  },
  heroEmoji: {
    fontSize: 48,
    lineHeight: 58,
  },
  orbitItem: {
    position: 'absolute',
    width: 48,
    height: 48,
    borderRadius: 24,
    alignItems: 'center',
    justifyContent: 'center',
  },
  orbitEmoji: {
    fontSize: 24,
    lineHeight: 30,
  },
  map: {
    backgroundColor: '#EEF2E6',
  },
  road: {
    position: 'absolute',
    backgroundColor: '#FFFFFF',
    borderRadius: 6,
  },
  road1: {
    left: -20,
    right: -20,
    top: '46%',
    height: 10,
    transform: [{ rotate: '-8deg' }],
  },
  road2: {
    top: -20,
    bottom: -20,
    left: '42%',
    width: 8,
    transform: [{ rotate: '12deg' }],
  },
  water: {
    position: 'absolute',
    right: -50,
    bottom: -60,
    width: 180,
    height: 160,
    borderRadius: 90,
    backgroundColor: '#BFDBE8',
  },
  pin: {
    position: 'absolute',
  },
  pinEmoji: {
    fontSize: 30,
    lineHeight: 38,
  },
  layerSwitch: {
    position: 'absolute',
    top: Spacing.two,
    left: 0,
    right: 0,
    flexDirection: 'row',
    justifyContent: 'center',
    gap: 6,
    zIndex: 2,
  },
  layerButton: {
    paddingHorizontal: 10,
    paddingVertical: 5,
    borderRadius: 999,
    backgroundColor: '#FFFFFF',
  },
  layerButtonActive: {
    backgroundColor: Brand.green,
  },
  layerLabel: {
    color: Brand.green,
    fontSize: 12,
    fontWeight: '600',
  },
  layerLabelActive: {
    color: '#FFFFFF',
  },
  preview: {
    position: 'absolute',
    left: Spacing.three,
    right: Spacing.three,
    bottom: Spacing.three,
  },
  row: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    padding: Spacing.two,
    borderRadius: Radius.md,
    borderWidth: StyleSheet.hairlineWidth,
  },
  thumb: {
    width: 40,
    height: 40,
    borderRadius: 10,
    alignItems: 'center',
    justifyContent: 'center',
  },
  thumbEmoji: {
    fontSize: 20,
    lineHeight: 26,
  },
  rowText: {
    flex: 1,
  },
  rowsHeader: {
    textAlign: 'center',
  },
  searchBar: {
    flexDirection: 'row',
    alignItems: 'center',
    gap: Spacing.two,
    paddingHorizontal: Spacing.three,
    minHeight: 48,
    borderRadius: 24,
    borderWidth: 2,
  },
  searchText: {
    flex: 1,
  },
  chipGroup: {
    gap: 6,
  },
  chipRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    gap: 6,
  },
  chip: {
    paddingHorizontal: 12,
    paddingVertical: 6,
    borderRadius: 999,
    borderWidth: 1,
  },
  flow: {
    alignItems: 'center',
  },
  flowItem: {
    alignItems: 'center',
    gap: 4,
  },
  flowArrow: {
    fontSize: 20,
  },
  flowStage: {
    paddingHorizontal: Spacing.three,
    paddingVertical: Spacing.two,
    borderRadius: 999,
  },
});
