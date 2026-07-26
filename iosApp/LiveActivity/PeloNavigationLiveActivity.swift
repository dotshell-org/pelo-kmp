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
            LockScreenView(state: context.state)
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
                    Text(context.state.instruction)
                        .font(.headline)
                        .lineLimit(2)
                        .minimumScaleFactor(0.8)
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
    let state: PeloNavigationAttributes.ContentState

    var body: some View {
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
