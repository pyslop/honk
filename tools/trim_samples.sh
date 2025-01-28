#!/bin/bash

# Create processed directory if it doesn't exist
mkdir -p processed

# Padding in seconds to add before and after the non-silent parts
PADDING=0.05

for file in ../app/src/main/res/raw/*.wav; do
    filename=$(basename "$file")
    echo "Processing $filename..."

    # Get silence detection values
    silence_data=$(ffmpeg -i "$file" -af silencedetect=noise=-30dB:d=0.1 -f null - 2>&1)

    # Extract start and end times
    start_time=$(echo "$silence_data" | grep "silence_end" | head -n1 | awk '{print $5}')
    end_time=$(echo "$silence_data" | grep "silence_start" | tail -n1 | awk '{print $5}')

    # If no silence detected, skip file
    if [ -z "$start_time" ] || [ -z "$end_time" ]; then
        echo "No silence detected in $filename, skipping..."
        continue
    fi

    # Adjust times with padding
    start_time=$(echo "$start_time - $PADDING" | bc)
    end_time=$(echo "$end_time + $PADDING" | bc)

    # Ensure start time isn't negative
    start_time=$(echo "if($start_time < 0) 0 else $start_time" | bc)

    # Trim the audio
    ffmpeg -i "$file" -ss $start_time -to $end_time -af "afade=t=in:st=$start_time:d=$PADDING,afade=t=out:st=$(echo "$end_time - $PADDING" | bc):d=$PADDING" "processed/${filename%.*}_trimmed.wav" -y

    echo "Created processed/${filename%.*}_trimmed.wav"
done

echo "Done processing all files!"
