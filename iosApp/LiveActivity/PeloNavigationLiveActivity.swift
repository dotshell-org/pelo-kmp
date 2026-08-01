import ActivityKit
import SwiftUI
import WidgetKit

/// Lock-screen and Dynamic Island rendering of an ongoing journey.
///
/// Everything shown is passed in already localised and formatted by the app: the extension is a
/// separate process with its own bundle, so resolving strings or times here would risk disagreeing
/// with the screen the traveller was just looking at.
@available(iOS 16.1, *)
struct PeloNavigationLiveActivity: Widget {

    var body: some WidgetConfiguration {
        ActivityConfiguration(for: PeloNavigationAttributes.self) { context in
            LockScreenView(destination: context.attributes.destination, state: context.state)
                .padding(16)
                .activityBackgroundTint(Color.black.opacity(0.75))
                .activitySystemActionForegroundColor(Color.white)
        } dynamicIsland: { context in
            DynamicIsland {
                DynamicIslandExpandedRegion(.leading) {
                    if let line = context.state.lineName {
                        LineBadge(line: line)
                    }
                }
                DynamicIslandExpandedRegion(.trailing) {
                    RemainingTime(state: context.state)
                }
                DynamicIslandExpandedRegion(.bottom) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text(context.state.instruction)
                            .font(.headline)
                            .lineLimit(2)
                            .minimumScaleFactor(0.8)
                            .frame(maxWidth: .infinity, alignment: .leading)

                        JourneyBar(state: context.state)

                        Text(context.attributes.destination)
                            .font(.caption2)
                            .foregroundColor(.white.opacity(0.7))
                            .lineLimit(1)
                    }
                }
            } compactLeading: {
                if let line = context.state.lineName {
                    Text(line).font(.caption2).bold()
                } else {
                    Image(systemName: "figure.walk")
                }
            } compactTrailing: {
                // The compact region is a few points wide: minutes only, no unit.
                Text("\(context.state.remainingMinutes)")
                    .font(.caption2)
                    .monospacedDigit()
            } minimal: {
                Image(systemName: context.state.isArrived ? "checkmark.circle.fill" : "location.fill")
            }
        }
    }
}

@available(iOS 16.1, *)
private struct LockScreenView: View {
    let destination: String
    let state: PeloNavigationAttributes.ContentState

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .center, spacing: 12) {
                if let line = state.lineName {
                    LineBadge(line: line)
                } else {
                    Image(systemName: "figure.walk")
                        .font(.title2)
                        .foregroundColor(.white)
                }

                Text(state.instruction)
                    .font(.headline)
                    .foregroundColor(.white)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity, alignment: .leading)

                RemainingTime(state: state)
            }

            JourneyBar(state: state)

            Text(destination)
                .font(.caption2)
                .foregroundColor(.white.opacity(0.7))
                .lineLimit(1)
        }
    }
}

/// The journey as a bar: every leg in its line's own colour, a mark where the traveller changes,
/// and a tracker where they are now.
///
/// Segment widths are proportional to their durations and the durations sum to the trip, so the
/// tracker can be placed by fraction alone.
@available(iOS 16.1, *)
private struct JourneyBar: View {
    let state: PeloNavigationAttributes.ContentState

    private static let height: CGFloat = 8
    /// The tracker overhangs the bar, so the row is taller than the bar itself.
    private static let rowHeight: CGFloat = 12

    var body: some View {
        // Zero-length segments would each still claim their minimum width and push the rest off
        // the end. Dropping them changes no sum.
        let segments = state.segments.filter { $0.seconds > 0 }
        let total = max(state.totalSeconds, 1)

        GeometryReader { geometry in
            let width = geometry.size.width

            ZStack(alignment: .leading) {
                HStack(spacing: 0) {
                    ForEach(Array(segments.enumerated()), id: \.offset) { _, segment in
                        Rectangle()
                            .fill(color(for: segment))
                            .frame(width: width * CGFloat(segment.seconds) / CGFloat(total))
                    }
                }
                .frame(height: Self.height)
                .clipShape(Capsule())

                ForEach(state.transferOffsetsSeconds, id: \.self) { offset in
                    Circle()
                        .strokeBorder(Color.black.opacity(0.6), lineWidth: 1)
                        .background(Circle().fill(Color.white))
                        .frame(width: Self.height, height: Self.height)
                        .offset(x: width * CGFloat(offset) / CGFloat(total) - Self.height / 2)
                }

                Circle()
                    .fill(Color.white)
                    .frame(width: Self.rowHeight, height: Self.rowHeight)
                    .shadow(radius: 1)
                    .offset(x: width * CGFloat(state.progressFraction) - Self.rowHeight / 2)
            }
            .frame(height: Self.rowHeight, alignment: .center)
        }
        .frame(height: Self.rowHeight)
    }

    private func color(for segment: PeloNavigationAttributes.Segment) -> Color {
        switch segment.kind {
        case .walk:
            return Color.white.opacity(0.45)
        case .wait:
            return Color.white.opacity(0.22)
        case .ride:
            guard let argb = segment.colorArgb else { return Color.white.opacity(0.65) }
            return Color(packedRgb: argb)
        }
    }
}

@available(iOS 16.1, *)
private extension Color {
    /// The shared code carries line colours as packed ARGB ints; the alpha is always opaque.
    init(packedRgb: Int) {
        self.init(
            .sRGB,
            red: Double((packedRgb >> 16) & 0xFF) / 255,
            green: Double((packedRgb >> 8) & 0xFF) / 255,
            blue: Double(packedRgb & 0xFF) / 255,
            opacity: 1
        )
    }
}

@available(iOS 16.1, *)
private struct LineBadge: View {
    let line: String

    var body: some View {
        Text(line)
            .font(.caption)
            .bold()
            .foregroundColor(.black)
            .padding(.horizontal, 8)
            .padding(.vertical, 4)
            .background(Color.white)
            .clipShape(Capsule())
    }
}

@available(iOS 16.1, *)
private struct RemainingTime: View {
    let state: PeloNavigationAttributes.ContentState

    var body: some View {
        VStack(alignment: .trailing, spacing: 0) {
            if state.isArrived {
                Image(systemName: "checkmark.circle.fill")
                    .font(.title2)
                    .foregroundColor(.green)
            } else {
                Text("\(state.remainingMinutes) min")
                    .font(.title3)
                    .bold()
                    .monospacedDigit()
                    .foregroundColor(.white)
                Text(state.arrivalTime)
                    .font(.caption2)
                    .foregroundColor(.white.opacity(0.7))
            }
        }
    }
}

@main
struct PeloLiveActivityBundle: WidgetBundle {
    var body: some Widget {
        if #available(iOS 16.1, *) {
            PeloNavigationLiveActivity()
        }
    }
}
