#!/usr/bin/env bash

# Paragraph-aware notebook DIFF
# 
# Algorithm:
# diff (jq 'del(.paragraphs)' note-a.json) (jq 'del(.paragraphs)' note-b.json)
#   + diff of paragrpahs[]  - full-text? by .id?
#     + diff of lines inside each paragraph
#
#./diff_notebooks.sh <path-to-note-a.json> <path-to-note-b.json>

hash "jq" 2>/dev/null || { echo >&2 "jq is not isntalled. Try 'brew install jq' and re-run."; exit 1; }

A="$1"
B="$2"

# diff note \wo paragraps
#path=".paragraphs[].text"

a_dir="tmp/a"
b_dir="tmp/b"
mkdir -p "${a_dir}/paragraphs"
mkdir -p "${b_dir}/paragraphs"

#set -x

path=".paragraphs"
jq "del(${path})" "${A}" > "${a_dir}/note.json"
jq "del(${path})" "${B}" > "${b_dir}/note.json"


unset i
all_pars="${a_dir}-par"
jq -c '.paragraphs[]' "${A}" > "${all_pars}"
while read content ; do
    #TODO(bzz): \\n -> \n
    printf '%s\n' "$content" > "${a_dir}/paragraphs/$((++i))"
done < "${all_pars}"
touch "${b_dir}/paragraphs/2"

unset i
all_pars="${b_dir}-par"
jq -c '.paragraphs[]' "${B}" > "${all_pars}"
while read content ; do
    #TODO(bzz): \\n -> \n
    printf '%s\n' "$content" > "${b_dir}/paragraphs/$((++i))"
    echo $(echo "$content" | jq '.text')
done < "${all_pars}"


diff -r "${a_dir}" "${b_dir}"

# diff paragraps
#jq ".paragraphs[]" "${A}" > "/tmp/a-par"
#jq ".paragraphs[]" "${B}" > "/tmp/b-par"


#rm "/tmp/a"
#rm "/tmp/b"
